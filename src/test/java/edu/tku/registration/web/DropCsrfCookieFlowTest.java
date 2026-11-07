package edu.tku.registration.web;

import static edu.tku.registration.web.ApiTestSupport.MSG_AUTH_REQUIRED;
import static edu.tku.registration.web.ApiTestSupport.MSG_CSRF_INVALID;
import static edu.tku.registration.web.ApiTestSupport.jsonError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.tku.registration.repository.AuditLogRepository;
import edu.tku.registration.repository.CourseRepository;
import edu.tku.registration.repository.RegistrationRepository;
import edu.tku.registration.support.PinnedClockConfig;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR4-EX5 with the real XSRF-TOKEN cookie flow. NEVER use csrf() here: it permanently wraps the CSRF
 * token repository of the cached context. The marker property gives this class its own context (own H2).
 * Rolled back after each test.
 */
@SpringBootTest(properties = "tku.test.context=drop-csrf-cookie-flow")
@AutoConfigureMockMvc
@Transactional
@Import(PinnedClockConfig.class)
class DropCsrfCookieFlowTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CourseRepository courseRepository;
    @Autowired private RegistrationRepository registrationRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private EntityManager entityManager;

    private Long ma101() {
        return courseRepository.findByCourseCodeAndSemester_Code("MA101", "115-1").orElseThrow().getId();
    }

    @Test
    @DisplayName("FR4-EX5: with the real cookie flow (cookie XSRF-TOKEN=v and header X-XSRF-TOKEN: v) the drop succeeds")
    void cookieFlowSucceeds() throws Exception {
        Long id = ma101();
        mockMvc.perform(delete("/api/registrations/" + id).with(user("410000003").roles("STUDENT"))
                        .cookie(new Cookie("XSRF-TOKEN", "v")).header("X-XSRF-TOKEN", "v"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Successfully dropped MA101."));
    }

    @Test
    @DisplayName("FR4-EX5: with the real cookie flow, a missing or mismatching token gives 403 and an anonymous call gives 401; nothing is written")
    void cookieFlowFailures() throws Exception {
        Long id = ma101();
        entityManager.flush();
        long regs = registrationRepository.count();
        long audits = auditLogRepository.count();

        mockMvc.perform(delete("/api/registrations/" + id).with(user("410000003").roles("STUDENT")))
                .andExpect(jsonError(403, MSG_CSRF_INVALID));
        mockMvc.perform(delete("/api/registrations/" + id).with(user("410000003").roles("STUDENT"))
                        .cookie(new Cookie("XSRF-TOKEN", "v")).header("X-XSRF-TOKEN", "other"))
                .andExpect(jsonError(403, MSG_CSRF_INVALID));
        mockMvc.perform(delete("/api/registrations/" + id)
                        .cookie(new Cookie("XSRF-TOKEN", "v")).header("X-XSRF-TOKEN", "v"))
                .andExpect(jsonError(401, MSG_AUTH_REQUIRED));

        entityManager.flush();
        assertThat(registrationRepository.count()).isEqualTo(regs);
        assertThat(auditLogRepository.count()).isEqualTo(audits);
        entityManager.clear();
        assertThat(courseRepository.findById(id).orElseThrow().getAvailableSeats()).isZero();
    }
}
