package edu.tku.registration.security;

import static edu.tku.registration.web.ApiTestSupport.MSG_AUTH_REQUIRED;
import static edu.tku.registration.web.ApiTestSupport.jsonError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** NFR2: anonymous access to the JSON API gets 401 JSON, never a redirect; static assets stay public. */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("NFR2-AC1: anonymous GET /api/dashboard returns 401 'Authentication required.' with no redirect")
    void dashboardRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/dashboard"))
                .andExpect(jsonError(401, MSG_AUTH_REQUIRED))
                .andExpect(header().doesNotExist("Location"))
                .andExpect(unauthenticated());
    }

    @ParameterizedTest(name = "NFR2-AC2: anonymous GET {0}")
    @ValueSource(strings = {"/api/auth/me", "/api/courses", "/api/registrations", "/api/anything/else"})
    @DisplayName("NFR2-AC2: anonymous GET of any /api path returns 401 'Authentication required.'")
    void everyApiPathRequiresLogin(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(jsonError(401, MSG_AUTH_REQUIRED))
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    @DisplayName("NFR2-AC3: anonymous POST /api/registrations with CSRF returns 401 and changes no registration or audit rows")
    void anonymousRegistrationPostChangesNothing() throws Exception {
        int registrationsBefore = count("registration");
        int auditBefore = count("audit_log");

        mockMvc.perform(post("/api/registrations").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":1}"))
                .andExpect(jsonError(401, MSG_AUTH_REQUIRED))
                .andExpect(header().doesNotExist("Location"));

        assertThat(count("registration")).isEqualTo(registrationsBefore);
        assertThat(count("audit_log")).isEqualTo(auditBefore);
    }

    @ParameterizedTest(name = "NFR2-AC7: anonymous GET {0}")
    @ValueSource(strings = {"/assets/missing.js", "/favicon.svg"})
    @DisplayName("NFR2-AC7: static asset paths are public (never 401 or a redirect)")
    void staticAssetsArePublic(String path) throws Exception {
        int status = mockMvc.perform(get(path)).andReturn().getResponse().getStatus();

        assertThat(status).as("status of %s", path).isNotEqualTo(401);
        assertThat(status / 100).as("status class of %s (3xx = redirect)", path).isNotEqualTo(3);
    }

    private int count(String table) {
        Integer n = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return n == null ? 0 : n;
    }
}
