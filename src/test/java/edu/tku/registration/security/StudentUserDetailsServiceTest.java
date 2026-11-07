package edu.tku.registration.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.tku.registration.domain.Student;
import edu.tku.registration.repository.StudentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@SpringBootTest
@AutoConfigureMockMvc
class StudentUserDetailsServiceTest {

    @Autowired
    private StudentUserDetailsService userDetailsService;

    @Autowired
    private StudentRepository studentRepository;

    @Test
    @DisplayName("NFR2-AC6: loadUserByUsername returns the student with BCrypt hash and ROLE_STUDENT")
    void loadsExistingStudent() {
        Student alice = studentRepository.findByStudentId("410000001").orElseThrow();

        UserDetails details = userDetailsService.loadUserByUsername("410000001");

        assertThat(details.getUsername()).isEqualTo("410000001");
        assertThat(details.getPassword()).isEqualTo(alice.getPasswordHash());
        assertThat(details.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_STUDENT");
    }

    @Test
    @DisplayName("NFR2-AC6: loadUserByUsername throws UsernameNotFoundException for an unknown student ID")
    void unknownStudentThrows() {
        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("499999999"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
