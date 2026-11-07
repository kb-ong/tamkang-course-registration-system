package edu.tku.registration.service;

import static org.assertj.core.api.Assertions.assertThat;

import edu.tku.registration.domain.Registration;
import edu.tku.registration.domain.Semester;
import edu.tku.registration.repository.CourseRepository;
import edu.tku.registration.repository.RegistrationRepository;
import edu.tku.registration.repository.SemesterRepository;
import edu.tku.registration.repository.StudentRepository;
import edu.tku.registration.support.MutableTestClock;
import edu.tku.registration.support.PinnedClockConfig;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/** ScheduleService (FR5-AC2 service half, FR5-EX2). Rolled back after each test. */
@SpringBootTest
@Transactional
@Import(PinnedClockConfig.class)
class ScheduleServiceTest {

    @Autowired private ScheduleService scheduleService;
    @Autowired private RegistrationService registrationService;
    @Autowired private SemesterRepository semesterRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private RegistrationRepository registrationRepository;
    @Autowired private MutableTestClock clock;

    @BeforeEach
    void setUp() {
        clock.set(MutableTestClock.T_OPEN);
    }

    private void insert(String studentId, String courseCode, String semester) {
        registrationRepository.saveAndFlush(new Registration(
                studentRepository.findByStudentId(studentId).orElseThrow(),
                courseRepository.findByCourseCodeAndSemester_Code(courseCode, semester).orElseThrow(),
                LocalDateTime.parse("2026-09-01T09:00:00")));
    }

    @Test
    @DisplayName("FR5-AC2: getSchedule computes totalCredits from the listed rows only (selected semester)")
    void totals() {
        RegistrationSchedule none = scheduleService.getSchedule("410000001", null);
        assertThat(none.courses()).isEmpty();
        assertThat(none.totalCredits()).isZero();

        insert("410000001", "CS101", "115-1");
        insert("410000001", "CS201", "115-1");
        insert("410000001", "CS102", "114-2");

        RegistrationSchedule fall = scheduleService.getSchedule("410000001", null);
        assertThat(fall.courses()).extracting(RegisteredCourse::code).containsExactly("CS101", "CS201");
        assertThat(fall.totalCredits()).isEqualTo(6);

        RegistrationSchedule spring = scheduleService.getSchedule("410000001", "114-2");
        assertThat(spring.courses()).extracting(RegisteredCourse::code).containsExactly("CS102");
        assertThat(spring.totalCredits()).isEqualTo(3);

        assertThat(scheduleService.getSchedule("410000003", null).totalCredits()).isEqualTo(4);
    }

    @Test
    @DisplayName("FR5-AC2: after register and drop through the services the total follows (6 then 3)")
    void totalsAfterRegisterAndDrop() {
        Long cs101 = courseRepository.findByCourseCodeAndSemester_Code("CS101", "115-1").orElseThrow().getId();
        Long cs201 = courseRepository.findByCourseCodeAndSemester_Code("CS201", "115-1").orElseThrow().getId();
        registrationService.register("410000001", cs101);
        registrationService.register("410000001", cs201);
        assertThat(scheduleService.getSchedule("410000001", null).totalCredits()).isEqualTo(6);
        registrationService.drop("410000001", cs201);
        assertThat(scheduleService.getSchedule("410000001", null).totalCredits()).isEqualTo(3);
    }

    @Test
    @DisplayName("FR5-EX2: with no current semester the schedule is empty with null semester and period; explicit semester still works")
    void noCurrentSemester() {
        Semester s = semesterRepository.findByCode("115-1").orElseThrow();
        s.setCurrent(false);
        semesterRepository.saveAndFlush(s);

        RegistrationSchedule r = scheduleService.getSchedule("410000001", null);
        assertThat(r.semester()).isNull();
        assertThat(r.registrationPeriod()).isNull();
        assertThat(r.courses()).isEmpty();
        assertThat(r.totalCredits()).isZero();
        assertThat(r.semesters()).extracting(SemesterOption::code).containsExactly("115-1", "114-2");

        RegistrationSchedule carol = scheduleService.getSchedule("410000003", "115-1");
        assertThat(carol.courses()).extracting(RegisteredCourse::code).containsExactly("MA101");
    }
}
