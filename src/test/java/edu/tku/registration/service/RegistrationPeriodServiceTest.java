package edu.tku.registration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.tku.registration.support.MutableTestClock;
import edu.tku.registration.support.PinnedClockConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** FR7-AC1b service half. Rejected registrations write nothing, so no cleanup is needed. */
@SpringBootTest
@Import(PinnedClockConfig.class)
class RegistrationPeriodServiceTest {

    @Autowired private RegistrationService registrationService;
    @Autowired private MutableTestClock clock;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        clock.set(MutableTestClock.T_OPEN);
    }

    @ParameterizedTest(name = "FR7-AC1b: register at {0} throws RegistrationClosedException")
    @ValueSource(strings = {MutableTestClock.T_BEFORE_START, MutableTestClock.T_AFTER_END, MutableTestClock.T_LATE})
    @DisplayName("FR7-AC1b: register outside the period throws RegistrationClosedException and changes nothing")
    void closedThrows(String instant) {
        Long cs101 = jdbc.queryForObject(
                "select id from course where course_code = 'CS101'", Long.class);
        int regs = jdbc.queryForObject("select count(*) from registration", Integer.class);
        int audits = jdbc.queryForObject("select count(*) from audit_log", Integer.class);
        long version = jdbc.queryForObject("select version from course where id = ?", Long.class, cs101);
        clock.set(instant);

        assertThatThrownBy(() -> registrationService.register("410000001", cs101))
                .isInstanceOf(RegistrationClosedException.class)
                .hasMessage("Registration is closed for semester 115-1.");

        assertThat(jdbc.queryForObject("select available_seats from course where id = ?", Integer.class, cs101))
                .isEqualTo(50);
        assertThat(jdbc.queryForObject("select version from course where id = ?", Long.class, cs101))
                .isEqualTo(version);
        assertThat(jdbc.queryForObject("select count(*) from registration", Integer.class)).isEqualTo(regs);
        assertThat(jdbc.queryForObject("select count(*) from audit_log", Integer.class)).isEqualTo(audits);
    }

    @Test
    @DisplayName("FR7-AC1b: the exception message is 'Registration is closed for semester {code}.'")
    void message() {
        assertThat(new RegistrationClosedException("115-1").getMessage())
                .isEqualTo("Registration is closed for semester 115-1.");
        assertThat(new DropClosedException("115-1").getMessage())
                .isEqualTo("Dropping is closed for semester 115-1.");
        assertThat(new NotRegisteredException("CS101").getMessage())
                .isEqualTo("You are not registered for CS101.");
    }
}
