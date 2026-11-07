package edu.tku.registration.web;

import static edu.tku.registration.web.ApiTestSupport.ALICE_ID;
import static edu.tku.registration.web.ApiTestSupport.ALICE_JSON;
import static edu.tku.registration.web.ApiTestSupport.MSG_CSRF_INVALID;
import static edu.tku.registration.web.ApiTestSupport.PASSWORD;
import static edu.tku.registration.web.ApiTestSupport.cookiesNamed;
import static edu.tku.registration.web.ApiTestSupport.jsonError;
import static edu.tku.registration.web.ApiTestSupport.loginBody;
import static edu.tku.registration.web.ApiTestSupport.loginRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Real XSRF-TOKEN cookie flow, without spring-security-test's csrf() post-processor (FR1-EX9, EX10, EX12).
 *
 * <p>Isolation: csrf() permanently wraps the shared CsrfFilter's CsrfTokenRepository in a
 * TestCsrfTokenRepository, after which that cached context no longer writes the real cookie.
 * The marker property gives this class its own Spring context (and so its own H2 database),
 * which no csrf()-using test shares. NEVER use csrf() in this class.
 */
@SpringBootTest(properties = "tku.test.context=csrf-cookie-flow")
@AutoConfigureMockMvc
class CsrfCookieFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("FR1-EX9: anonymous GET /api/auth/csrf returns 204 and sets a JS-readable XSRF-TOKEN cookie on path /")
    void csrfBootstrap() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().path("XSRF-TOKEN", "/"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false))
                .andReturn();

        assertThat(result.getResponse().getCookie("XSRF-TOKEN").getValue()).isNotBlank();
        assertThat(result.getResponse().getContentAsString()).isEmpty();
    }

    @Test
    @DisplayName("FR1-EX10: real cookie flow, (a) login with matching XSRF-TOKEN cookie and X-XSRF-TOKEN header returns 200")
    void realCookieFlowValidToken() throws Exception {
        String token = bootstrapCsrfToken();

        mockMvc.perform(loginRequest(loginBody(ALICE_ID, PASSWORD))
                        .cookie(new Cookie("XSRF-TOKEN", token))
                        .header("X-XSRF-TOKEN", token))
                .andExpect(status().isOk())
                .andExpect(content().json(ALICE_JSON, JsonCompareMode.STRICT));
    }

    @Test
    @DisplayName("FR1-EX10: real cookie flow, (b) login with a mismatching X-XSRF-TOKEN header returns 403 'Invalid or missing CSRF token.'")
    void realCookieFlowWrongToken() throws Exception {
        String token = bootstrapCsrfToken();

        mockMvc.perform(loginRequest(loginBody(ALICE_ID, PASSWORD))
                        .cookie(new Cookie("XSRF-TOKEN", token))
                        .header("X-XSRF-TOKEN", "not-the-token"))
                .andExpect(jsonError(403, MSG_CSRF_INVALID))
                .andExpect(unauthenticated());
    }

    @Test
    @DisplayName("FR1-EX12: a successful login clears the pre-login XSRF-TOKEN cookie (max-age 0)")
    void loginClearsCsrfCookie() throws Exception {
        String token = bootstrapCsrfToken();

        MvcResult result = mockMvc.perform(loginRequest(loginBody(ALICE_ID, PASSWORD))
                        .cookie(new Cookie("XSRF-TOKEN", token))
                        .header("X-XSRF-TOKEN", token))
                .andExpect(status().isOk())
                .andReturn();

        List<Cookie> xsrf = cookiesNamed(result.getResponse(), "XSRF-TOKEN");
        assertThat(xsrf).as("XSRF-TOKEN cookies set by the login response").isNotEmpty();
        assertThat(xsrf.get(xsrf.size() - 1).getMaxAge())
                .as("max-age of the last XSRF-TOKEN cookie set by the login response")
                .isZero();
    }

    private String bootstrapCsrfToken() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isNoContent())
                .andReturn();
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).as("XSRF-TOKEN cookie from /api/auth/csrf").isNotNull();
        assertThat(cookie.getValue()).isNotBlank();
        return cookie.getValue();
    }
}
