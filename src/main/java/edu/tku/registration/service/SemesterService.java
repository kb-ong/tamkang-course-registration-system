package edu.tku.registration.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.tku.registration.domain.RegistrationPeriod;
import edu.tku.registration.domain.Semester;
import edu.tku.registration.repository.RegistrationPeriodRepository;
import edu.tku.registration.repository.SemesterRepository;

@Service
public class SemesterService {

    private final SemesterRepository semesterRepository;
    private final RegistrationPeriodRepository registrationPeriodRepository;
    private final Clock clock;

    public SemesterService(SemesterRepository semesterRepository,
                           RegistrationPeriodRepository registrationPeriodRepository,
                           Clock clock) {
        this.semesterRepository = semesterRepository;
        this.registrationPeriodRepository = registrationPeriodRepository;
        this.clock = clock;
    }

    /**
     * Returns the current semester with its registration period, or empty if no semester is current.
     * The period bounds are inclusive; if the semester has no period, both dates are null and
     * registration is closed.
     */
    @Transactional(readOnly = true)
    public Optional<CurrentSemesterInfo> getCurrentSemesterInfo() {
        return semesterRepository.findByCurrentTrue().map(semester -> {
            RegistrationPeriodInfo p = getRegistrationPeriod(semester.getCode());
            return new CurrentSemesterInfo(semester.getCode(), semester.getName(), p.startAt(), p.endAt(), p.open());
        });
    }

    @Transactional(readOnly = true)
    public RegistrationPeriodInfo getRegistrationPeriod(String semesterCode) {
        Optional<RegistrationPeriod> period = semesterCode == null ? Optional.empty()
                : registrationPeriodRepository.findBySemester_Code(semesterCode);
        if (period.isEmpty()) {
            return new RegistrationPeriodInfo(null, null, false);
        }
        LocalDateTime startAt = period.get().getStartAt();
        LocalDateTime endAt = period.get().getEndAt();
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        boolean open = !now.isBefore(startAt) && !now.isAfter(endAt);
        return new RegistrationPeriodInfo(startAt, endAt, open);
    }

    public boolean isRegistrationOpen(String semesterCode) {
        return getRegistrationPeriod(semesterCode).open();
    }
}
