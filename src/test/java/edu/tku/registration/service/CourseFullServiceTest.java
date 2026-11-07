package edu.tku.registration.service;

import static edu.tku.registration.service.CapacityTestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.tku.registration.repository.CourseRepository;
import edu.tku.registration.repository.SemesterRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

/** FR6 service-level criteria. Not @Transactional; throw-away courses are removed after each test. */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CourseFullServiceTest {

    private static final String ALICE = "410000001";
    private static final String BOB = "410000002";
    private static final String CAROL = "410000003";
    private static final String ZERO_MSG = "Course Full: ZERO1 has no available seats.";
    private static final String MA_MSG = "Course Full: MA101 has no available seats.";
    private static final String CONFLICT = "The course was just updated by another student. Please try again.";

    @Autowired private RegistrationService registrationService;
    @Autowired private CourseRepository courseRepository;
    @Autowired private SemesterRepository semesterRepository;
    @Autowired private JdbcTemplate jdbc;

    private Long ma101;
    private Long zero1;

    @BeforeEach
    void setUp() {
        ma101 = courseRepository.findByCourseCodeAndSemester_Code("MA101", "115-1").orElseThrow().getId();
        zero1 = createCourse(courseRepository, semesterRepository, "ZERO1", 5, 0);
    }

    @AfterEach
    void tearDown() {
        removeCourse(jdbc, zero1);
    }

    @Test
    @DisplayName("FR6-AC1: register on a 0-seat course throws CourseFullException; seats and version unchanged")
    void blockedAtZeroSeats() {
        long v = version(jdbc, ma101);
        assertThatThrownBy(() -> registrationService.register(BOB, ma101)).isInstanceOf(CourseFullException.class);
        assertThat(seats(jdbc, ma101)).isZero();
        assertThat(version(jdbc, ma101)).isEqualTo(v);

        long vz = version(jdbc, zero1);
        assertThatThrownBy(() -> registrationService.register(ALICE, zero1)).isInstanceOf(CourseFullException.class);
        assertThat(seats(jdbc, zero1)).isZero();
        assertThat(version(jdbc, zero1)).isEqualTo(vz);
    }

    @Test
    @DisplayName("FR6-AC2a: the exception message is exactly 'Course Full: {code} has no available seats.'")
    void messageExact() {
        assertThat(new CourseFullException("MA101").getMessage()).isEqualTo(MA_MSG);
        assertThatThrownBy(() -> registrationService.register(BOB, ma101))
                .isInstanceOf(CourseFullException.class).hasMessage(MA_MSG);
        assertThatThrownBy(() -> registrationService.register(ALICE, zero1))
                .isInstanceOf(CourseFullException.class).hasMessage(ZERO_MSG);
    }

    @Test
    @DisplayName("FR6-AC2b: the message contains 'Course Full' and never the generic conflict text")
    void messageContainsCourseFull() {
        assertThatThrownBy(() -> registrationService.register(BOB, ma101))
                .hasMessageContaining("Course Full").message().doesNotContain(CONFLICT);
        assertThatThrownBy(() -> registrationService.register(ALICE, zero1))
                .hasMessageContaining("Course Full").message().doesNotContain(CONFLICT);
    }

    @Test
    @DisplayName("FR6-AC3a: a rejected attempt leaves registration rows, audit rows, seats and version unchanged")
    void noRecordCreated() {
        int regsBefore = totalRegs(jdbc);
        int auditsBefore = totalAudits(jdbc);
        long v = version(jdbc, ma101);
        long vz = version(jdbc, zero1);

        assertThatThrownBy(() -> registrationService.register(BOB, ma101)).isInstanceOf(CourseFullException.class);
        assertThatThrownBy(() -> registrationService.register(ALICE, zero1)).isInstanceOf(CourseFullException.class);

        assertThat(totalRegs(jdbc)).isEqualTo(regsBefore);
        assertThat(totalAudits(jdbc)).isEqualTo(auditsBefore);
        assertThat(seats(jdbc, ma101)).isZero();
        assertThat(seats(jdbc, zero1)).isZero();
        assertThat(version(jdbc, ma101)).isEqualTo(v);
        assertThat(version(jdbc, zero1)).isEqualTo(vz);
    }

    @Test
    @DisplayName("FR6-AC3b: the last seat goes to the first student, the second gets Course Full; one registration, one audit row")
    void lastSeatThenFull() {
        Long last1 = createCourse(courseRepository, semesterRepository, "LAST1", 1, 1);
        try {
            registrationService.register(ALICE, last1);
            assertThat(seats(jdbc, last1)).isZero();

            assertThatThrownBy(() -> registrationService.register(BOB, last1))
                    .isInstanceOf(CourseFullException.class)
                    .hasMessage("Course Full: LAST1 has no available seats.");

            assertThat(seats(jdbc, last1)).isZero();
            assertThat(regs(jdbc, last1)).isEqualTo(1);
            assertThat(audits(jdbc, last1)).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "select student_id from audit_log where course_id = ? and action = 'REGISTER'",
                    String.class, last1)).isEqualTo(ALICE);
            // FR6-EX5
            assertThat(invariantGap(jdbc, last1)).isZero();
        } finally {
            removeCourse(jdbc, last1);
        }
    }

    @Test
    @DisplayName("FR6-EX4: on seed data availableSeats + registrations = capacity for every course")
    void seedInvariant() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select c.course_code as code, c.capacity as capacity, c.available_seats as seats,
                       (select count(*) from registration r where r.course_id = c.id) as regs
                from course c where c.course_code <> 'ZERO1' order by c.course_code""");
        assertThat(rows).isNotEmpty();
        for (Map<String, Object> r : rows) {
            long sum = ((Number) r.get("seats")).longValue() + ((Number) r.get("regs")).longValue();
            assertThat(sum).as("invariant for %s", r.get("code")).isEqualTo(((Number) r.get("capacity")).longValue());
        }
        Map<String, Long> seats = new java.util.HashMap<>();
        Map<String, Long> regs = new java.util.HashMap<>();
        for (Map<String, Object> r : rows) {
            seats.put((String) r.get("code"), ((Number) r.get("seats")).longValue());
            regs.put((String) r.get("code"), ((Number) r.get("regs")).longValue());
        }
        assertThat(seats).containsEntry("CS101", 50L).containsEntry("CS201", 40L)
                .containsEntry("MA101", 0L).containsEntry("CS102", 45L);
        assertThat(regs).containsEntry("CS101", 0L).containsEntry("CS201", 0L)
                .containsEntry("MA101", 1L).containsEntry("CS102", 0L);
    }

    @Test
    @DisplayName("FR6-EX6: check order is unchanged: already-registered beats full; unknown course and student keep their errors")
    void checkOrder() {
        assertThatThrownBy(() -> registrationService.register(CAROL, ma101))
                .isInstanceOf(AlreadyRegisteredException.class)
                .hasMessage("You are already registered for MA101.");
        assertThatThrownBy(() -> registrationService.register(ALICE, 999999L))
                .isInstanceOf(CourseNotFoundException.class);
        assertThatThrownBy(() -> registrationService.register("499999999", ma101))
                .isInstanceOf(StudentNotFoundException.class);
    }

    @Test
    @DisplayName("FR6-EX8: a course with seats left is unaffected: success, seat decrement, audit row")
    void seatsLeftUnaffected() {
        Long c = createCourse(courseRepository, semesterRepository, "LEFT2", 2, 2);
        try {
            registrationService.register(ALICE, c);
            assertThat(seats(jdbc, c)).isEqualTo(1);
            assertThat(regs(jdbc, c)).isEqualTo(1);
            assertThat(audits(jdbc, c)).isEqualTo(1);
            assertThat(invariantGap(jdbc, c)).isZero();
        } finally {
            removeCourse(jdbc, c);
        }
    }
}
