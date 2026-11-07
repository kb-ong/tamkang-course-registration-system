package edu.tku.registration.service;

import static org.assertj.core.api.Assertions.assertThat;

import edu.tku.registration.repository.RegistrationPeriodRepository;
import edu.tku.registration.repository.SemesterRepository;
import edu.tku.registration.support.PeriodTestData;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** FR7-EX4: SemesterService period rules with a fixed Clock (Asia/Taipei). */
@SpringBootTest
class SemesterPeriodServiceTest {

    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");

    @Autowired private SemesterRepository semesterRepository;
    @Autowired private RegistrationPeriodRepository registrationPeriodRepository;

    private SemesterService serviceAt(String taipeiLocal) {
        Clock clock = Clock.fixed(LocalDateTime.parse(taipeiLocal).atZone(TAIPEI).toInstant(), TAIPEI);
        return new SemesterService(semesterRepository, registrationPeriodRepository, clock);
    }

    @ParameterizedTest(name = "FR7-EX4: isRegistrationOpen(115-1) at {0} = {1}")
    @CsvSource({
            "2026-08-01T00:00:00, true",
            "2026-12-31T23:59:59, true",
            "2026-12-31T23:59:59.999999999, true",
            "2026-07-31T23:59:59.999, false",
            "2027-01-01T00:00:00, false"
    })
    @DisplayName("FR7-EX4: isRegistrationOpen is inclusive at both bounds and truncates now to whole seconds")
    void bounds(String local, boolean expected) {
        SemesterService service = serviceAt(local);
        assertThat(service.isRegistrationOpen("115-1")).isEqualTo(expected);
        assertThat(service.getRegistrationPeriod("115-1").open()).isEqualTo(expected);
    }

    @Test
    @DisplayName("FR7-EX4: the dashboard rule (getCurrentSemesterInfo) agrees with isRegistrationOpen at the truncated end second")
    void dashboardAgrees() {
        assertThat(serviceAt("2026-12-31T23:59:59.999").getCurrentSemesterInfo().orElseThrow().registrationOpen())
                .isTrue();
        assertThat(serviceAt("2027-01-01T00:00:00").getCurrentSemesterInfo().orElseThrow().registrationOpen())
                .isFalse();
    }

    @Test
    @DisplayName("FR7-EX4: 114-2 is closed at T_OPEN, an unknown semester is closed")
    void otherAndUnknown() {
        SemesterService service = serviceAt("2026-09-01T09:00:00");
        assertThat(service.isRegistrationOpen("114-2")).isFalse();
        assertThat(service.isRegistrationOpen("NOPE-9")).isFalse();
        assertThat(service.getRegistrationPeriod("NOPE-9")).isEqualTo(new RegistrationPeriodInfo(null, null, false));
    }

    @Test
    @DisplayName("FR7-EX4: getRegistrationPeriod returns the period dates and the open flag")
    void periodInfo() {
        assertThat(serviceAt("2026-09-01T09:00:00").getRegistrationPeriod("115-1")).isEqualTo(
                new RegistrationPeriodInfo(LocalDateTime.parse("2026-08-01T00:00:00"),
                        LocalDateTime.parse("2026-12-31T23:59:59"), true));
        assertThat(serviceAt("2027-06-01T09:00:00").getRegistrationPeriod("115-1")).isEqualTo(
                new RegistrationPeriodInfo(LocalDateTime.parse("2026-08-01T00:00:00"),
                        LocalDateTime.parse("2026-12-31T23:59:59"), false));
    }

    @Test
    @Transactional
    @DisplayName("FR7-EX4: a semester without a period gives RegistrationPeriodInfo(null, null, false)")
    void noPeriod() {
        PeriodTestData.semester(semesterRepository, registrationPeriodRepository, "NOPD-1", null, null);
        SemesterService service = serviceAt("2026-09-01T09:00:00");
        assertThat(service.getRegistrationPeriod("NOPD-1")).isEqualTo(new RegistrationPeriodInfo(null, null, false));
        assertThat(service.isRegistrationOpen("NOPD-1")).isFalse();
    }
}
