package edu.tku.registration.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.tku.registration.domain.Course;
import edu.tku.registration.domain.Registration;
import edu.tku.registration.domain.Student;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/** @Transactional: the test transaction is rolled back, so seed data is untouched. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RegistrationConstraintTest {

    @Autowired private StudentRepository studentRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private SemesterRepository semesterRepository;
    @Autowired private RegistrationRepository registrationRepository;

    @Test
    @DisplayName("SK-AC5: a duplicate (student, course) registration violates the unique constraint")
    void duplicateRegistrationRejected() {
        Student carol = studentRepository.findByStudentId("410000003").orElseThrow();
        Course ma101 = courseRepository.findByCourseCodeAndSemester_Code("MA101", "115-1").orElseThrow();

        assertThatThrownBy(() -> registrationRepository.saveAndFlush(
                new Registration(carol, ma101, LocalDateTime.of(2026, 9, 1, 9, 0))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("SK-AC6: repository finder methods return the expected seed rows")
    void finderMethods() {
        assertThat(studentRepository.findByStudentId("410000002"))
                .get().extracting(Student::getName).isEqualTo("Bob Lin");

        Course cs102 = courseRepository.findByCourseCodeAndSemester_Code("CS102", "114-2").orElseThrow();
        assertThat(cs102.getCreditHours()).isEqualTo(3);
        assertThat(cs102.getAvailableSeats()).isEqualTo(45);

        assertThat(semesterRepository.findByCurrentTrue())
                .get().extracting(s -> s.getCode()).isEqualTo("115-1");
    }
}
