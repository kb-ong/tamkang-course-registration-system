package edu.tku.registration.service;

import static org.assertj.core.api.Assertions.assertThat;

import edu.tku.registration.domain.Semester;
import edu.tku.registration.repository.RegistrationPeriodRepository;
import edu.tku.registration.repository.SemesterRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** SemesterService.getCurrentSemesterInfo (spec FR1-login rev 2, §3.2). */
@SpringBootTest
@AutoConfigureMockMvc
class SemesterServiceTest {

    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");

    @Autowired private SemesterRepository semesterRepository;
    @Autowired private RegistrationPeriodRepository registrationPeriodRepository;
    @Autowired private SemesterService semesterService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private SemesterService serviceAt(LocalDateTime taipeiLocal) {
        Clock clock = Clock.fixed(taipeiLocal.atZone(TAIPEI).toInstant(), TAIPEI);
        return new SemesterService(semesterRepository, registrationPeriodRepository, clock);
    }

    @ParameterizedTest(name = "FR1-EX17: at {0} registrationOpen = {1}")
    @CsvSource({
            "2026-08-01T00:00:00, true",
            "2026-12-31T23:59:59, true",
            "2026-07-31T23:59:59, false",
            "2027-01-01T00:00:00, false"
    })
    @DisplayName("FR1-EX17: registrationOpen uses inclusive start and end bounds in Asia/Taipei local time")
    void registrationOpenBounds(String localDateTime, boolean expectedOpen) {
        Optional<CurrentSemesterInfo> info = serviceAt(LocalDateTime.parse(localDateTime)).getCurrentSemesterInfo();

        assertThat(info).isPresent();
        assertThat(info.get().registrationOpen()).isEqualTo(expectedOpen);
    }

    @Test
    @DisplayName("FR1-EX17: the current semester info carries code, name and the period bounds of 115-1")
    void currentSemesterFields() {
        CurrentSemesterInfo info = serviceAt(LocalDateTime.parse("2026-09-01T09:00:00"))
                .getCurrentSemesterInfo().orElseThrow();

        assertThat(info).isEqualTo(new CurrentSemesterInfo("115-1", "2026 Fall",
                LocalDateTime.parse("2026-08-01T00:00:00"),
                LocalDateTime.parse("2026-12-31T23:59:59"),
                true));
    }

    @Test
    @Transactional
    @DisplayName("FR1-EX18: (a) with no current semester, getCurrentSemesterInfo() is empty")
    void noCurrentSemester() {
        clearCurrentFlagOf115_1();

        assertThat(semesterService.getCurrentSemesterInfo()).isEmpty();
    }

    @Test
    @Transactional
    @DisplayName("FR1-EX18: (b) a current semester with no registration period has null dates and registrationOpen false")
    void currentSemesterWithoutPeriod() {
        clearCurrentFlagOf115_1();
        semesterRepository.saveAndFlush(new Semester("116-1", "2027 Fall", true));

        Optional<CurrentSemesterInfo> info = semesterService.getCurrentSemesterInfo();

        assertThat(info).isPresent();
        assertThat(info.get().code()).isEqualTo("116-1");
        assertThat(info.get().registrationStartAt()).isNull();
        assertThat(info.get().registrationEndAt()).isNull();
        assertThat(info.get().registrationOpen()).isFalse();
    }

    /** Sets semester 115-1 to current=false inside the (rolled back) test transaction. */
    private void clearCurrentFlagOf115_1() {
        int updated = jdbcTemplate.update("UPDATE semester SET is_current = FALSE WHERE code = '115-1'");
        assertThat(updated).as("rows updated for 115-1").isEqualTo(1);
    }
}
