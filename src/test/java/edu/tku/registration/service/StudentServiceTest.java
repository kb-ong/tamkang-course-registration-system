package edu.tku.registration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.tku.registration.domain.Student;
import edu.tku.registration.repository.StudentRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
@AutoConfigureMockMvc
class StudentServiceTest {

    @Autowired
    private StudentService studentService;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("FR1-EX4: every seed password is stored as a BCrypt hash of the test password")
    void seedPasswordsAreBcrypt() {
        List<Student> students = studentRepository.findAll();
        assertThat(students).isNotEmpty();

        for (Student s : students) {
            String hash = s.getPasswordHash();
            assertThat(hash).as("hash of %s", s.getStudentId())
                    .matches("^\\$2[aby]\\$.*")
                    .isNotEqualTo("password123");
            assertThat(passwordEncoder.matches("password123", hash))
                    .as("password123 matches hash of %s", s.getStudentId())
                    .isTrue();
        }
    }

    @Test
    @DisplayName("FR1-EX5: getProfile for an unknown student throws StudentNotFoundException")
    void unknownStudentThrows() {
        assertThatThrownBy(() -> studentService.getProfile("499999999"))
                .isInstanceOf(StudentNotFoundException.class)
                .hasMessage("Student not found: 499999999");
    }

    @Test
    @DisplayName("FR1-EX5: getProfile returns name, student ID and email of an existing student (positive counterpart)")
    void existingStudentProfile() {
        assertThat(studentService.getProfile("410000001"))
                .isEqualTo(new StudentProfile("410000001", "Alice Chen", "410000001@o365.tku.edu.tw"));
    }
}
