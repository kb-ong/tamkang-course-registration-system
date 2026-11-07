package edu.tku.registration.service;

import static edu.tku.registration.service.CapacityTestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.tku.registration.domain.Course;
import edu.tku.registration.domain.Registration;
import edu.tku.registration.domain.Semester;
import edu.tku.registration.repository.CourseRepository;
import edu.tku.registration.repository.RegistrationPeriodRepository;
import edu.tku.registration.repository.RegistrationRepository;
import edu.tku.registration.repository.SemesterRepository;
import edu.tku.registration.repository.StudentRepository;
import edu.tku.registration.support.MutableTestClock;
import edu.tku.registration.support.PeriodTestData;
import edu.tku.registration.support.PinnedClockConfig;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

/** RegistrationService.drop, service level. Not @Transactional; throw-away data is removed after each test. */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(PinnedClockConfig.class)
class DropServiceTest {

    private static final String ALICE = "410000001";

    @Autowired private RegistrationService registrationService;
    @Autowired private CourseRepository courseRepository;
    @Autowired private SemesterRepository semesterRepository;
    @Autowired private RegistrationPeriodRepository periodRepository;
    @Autowired private RegistrationRepository registrationRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MutableTestClock clock;

    private Long courseId;

    @BeforeEach
    void setUp() {
        clock.set(MutableTestClock.T_OPEN);
    }

    @AfterEach
    void cleanUp() {
        if (courseId != null) {
            removeCourse(jdbc, courseId);
            courseId = null;
        }
        PeriodTestData.removeSemesters(jdbc, "NOPD-1");
    }

    private int dropAudits(Long id) {
        return jdbc.queryForObject(
                "select count(*) from audit_log where course_id = ? and action = 'DROP'", Integer.class, id);
    }

    @Test
    @DisplayName("NFR4-AC2: a successful drop returns the DropResult and writes exactly one DROP audit row in the same transaction")
    void dropSuccess() {
        courseId = createCourse(courseRepository, semesterRepository, "DROP1", 5, 5);
        registrationService.register(ALICE, courseId);
        assertThat(seats(jdbc, courseId)).isEqualTo(4);

        DropResult r = registrationService.drop(ALICE, courseId);

        assertThat(r.courseId()).isEqualTo(courseId);
        assertThat(r.courseCode()).isEqualTo("DROP1");
        assertThat(r.availableSeats()).isEqualTo(5);
        assertThat(seats(jdbc, courseId)).isEqualTo(5);
        assertThat(regs(jdbc, courseId)).isZero();
        assertThat(dropAudits(courseId)).isEqualTo(1);
        assertThat(audits(jdbc, courseId)).isEqualTo(2);
        assertThat(invariantGap(jdbc, courseId)).isZero();
    }

    @Test
    @DisplayName("NFR4-AC2: failed drops (not registered, unknown course, unknown student) write no audit row")
    void failedDropsNotAudited() {
        courseId = createCourse(courseRepository, semesterRepository, "DROP2", 5, 5);
        int before = totalAudits(jdbc);

        assertThatThrownBy(() -> registrationService.drop(ALICE, courseId))
                .isInstanceOf(NotRegisteredException.class).hasMessage("You are not registered for DROP2.");
        assertThatThrownBy(() -> registrationService.drop(ALICE, 999999L))
                .isInstanceOf(CourseNotFoundException.class);
        assertThatThrownBy(() -> registrationService.drop(ALICE, 0L))
                .isInstanceOf(CourseNotFoundException.class);
        assertThatThrownBy(() -> registrationService.drop(ALICE, null))
                .isInstanceOf(CourseNotFoundException.class);
        assertThatThrownBy(() -> registrationService.drop("499999999", courseId))
                .isInstanceOf(StudentNotFoundException.class);

        assertThat(totalAudits(jdbc)).isEqualTo(before);
        assertThat(seats(jdbc, courseId)).isEqualTo(5);
    }

    @ParameterizedTest(name = "FR4-EX3: drop at {0} throws DropClosedException")
    @ValueSource(strings = {MutableTestClock.T_LATE, MutableTestClock.T_BEFORE_START, MutableTestClock.T_AFTER_END})
    @DisplayName("FR4-EX3: outside the period drop throws DropClosedException 'Dropping is closed for semester 115-1.' and changes nothing")
    void closedOutsidePeriod(String instant) {
        Long ma101 = courseRepository.findByCourseCodeAndSemester_Code("MA101", "115-1").orElseThrow().getId();
        long version = version(jdbc, ma101);
        int audits = totalAudits(jdbc);
        clock.set(instant);

        assertThatThrownBy(() -> registrationService.drop("410000003", ma101))
                .isInstanceOf(DropClosedException.class)
                .hasMessage("Dropping is closed for semester 115-1.");

        assertThat(regs(jdbc, ma101)).isEqualTo(1);
        assertThat(seats(jdbc, ma101)).isZero();
        assertThat(version(jdbc, ma101)).isEqualTo(version);
        assertThat(totalAudits(jdbc)).isEqualTo(audits);
    }

    @Test
    @DisplayName("FR4-EX3: a semester without a period throws DropClosedException 'Dropping is closed for semester NOPD-1.'")
    void closedWithoutPeriod() {
        Semester s = PeriodTestData.semester(semesterRepository, periodRepository, "NOPD-1", null, null);
        Course c = PeriodTestData.course(courseRepository, s, "NP101", 5, 4);
        registrationRepository.saveAndFlush(new Registration(
                studentRepository.findByStudentId(ALICE).orElseThrow(), c, LocalDateTime.parse("2026-09-01T09:00:00")));

        assertThatThrownBy(() -> registrationService.drop(ALICE, c.getId()))
                .isInstanceOf(DropClosedException.class)
                .hasMessage("Dropping is closed for semester NOPD-1.");
        assertThat(regs(jdbc, c.getId())).isEqualTo(1);
        assertThat(seats(jdbc, c.getId())).isEqualTo(4);
        assertThat(audits(jdbc, c.getId())).isZero();
    }

    @Test
    @DisplayName("FR4-EX11: dropping with an inconsistent state never raises seats above capacity (2 stays 2)")
    void neverAboveCapacity() {
        courseId = createCourse(courseRepository, semesterRepository, "CAP2", 2, 2);
        Course c = courseRepository.findById(courseId).orElseThrow();
        registrationRepository.saveAndFlush(new Registration(
                studentRepository.findByStudentId(ALICE).orElseThrow(), c, LocalDateTime.parse("2026-09-01T09:00:00")));

        DropResult r = registrationService.drop(ALICE, courseId);

        assertThat(r.availableSeats()).isEqualTo(2);
        assertThat(regs(jdbc, courseId)).isZero();
        assertThat(seats(jdbc, courseId)).isEqualTo(2);
    }
}
