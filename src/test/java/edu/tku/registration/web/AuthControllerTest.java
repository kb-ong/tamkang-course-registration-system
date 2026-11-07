package edu.tku.registration.web;

import static edu.tku.registration.web.ApiTestSupport.ALICE_ID;
import static edu.tku.registration.web.ApiTestSupport.ALICE_JSON;
import static edu.tku.registration.web.ApiTestSupport.MSG_AUTH_REQUIRED;
import static edu.tku.registration.web.ApiTestSupport.MSG_CSRF_INVALID;
import static edu.tku.registration.web.ApiTestSupport.MSG_LOGIN_INVALID;
import static edu.tku.registration.web.ApiTestSupport.MSG_MALFORMED;
import static edu.tku.registration.web.ApiTestSupport.MSG_STUDENT_NOT_FOUND;
import static edu.tku.registration.web.ApiTestSupport.PASSWORD;
import static edu.tku.registration.web.ApiTestSupport.jsonError;
import static edu.tku.registration.web.ApiTestSupport.loginAsAlice;
import static edu.tku.registration.web.ApiTestSupport.loginBody;
import static edu.tku.registration.web.ApiTestSupport.loginRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * /api/auth/** endpoints: login, logout, me (spec FR1-login rev 2, §3.3/§3.4).
 * The real XSRF-TOKEN cookie flow (FR1-EX9, EX10, EX12) lives in {@link CsrfCookieFlowTest},
 * because the csrf() post-processor used here permanently wraps the shared CsrfFilter's repository.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ---------------------------------------------------------------- login

    @Test
    @DisplayName("FR1-AC1: valid JSON login returns 200 with Alice's profile, authenticates, and the session works for /api/auth/me")
    void validLogin() throws Exception {
        MvcResult result = mockMvc.perform(loginRequest(loginBody(ALICE_ID, PASSWORD)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json(ALICE_JSON, JsonCompareMode.STRICT))
                .andExpect(authenticated().withUsername(ALICE_ID))
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).as("session created by login").isNotNull();

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(content().json(ALICE_JSON, JsonCompareMode.STRICT));
    }

    @Test
    @DisplayName("FR1-AC2a: wrong password returns 401 'Invalid student ID or password.' and stays unauthenticated")
    void wrongPassword() throws Exception {
        mockMvc.perform(loginRequest(loginBody(ALICE_ID, "wrong-password")).with(csrf()))
                .andExpect(jsonError(401, MSG_LOGIN_INVALID))
                .andExpect(unauthenticated());
    }

    @Test
    @DisplayName("FR1-AC2b: unknown student ID returns 401 'Invalid student ID or password.' and stays unauthenticated")
    void unknownStudentId() throws Exception {
        mockMvc.perform(loginRequest(loginBody("499999999", PASSWORD)).with(csrf()))
                .andExpect(jsonError(401, MSG_LOGIN_INVALID))
                .andExpect(unauthenticated());
    }

    @ParameterizedTest(name = "FR1-EX2: body {0}")
    @ValueSource(strings = {"{\"studentId\":\"\",\"password\":\"\"}", "{}", "{\"studentId\":\"410000001\"}"})
    @DisplayName("FR1-EX2: blank or missing login fields return 401 'Invalid student ID or password.'")
    void blankOrMissingFields(String body) throws Exception {
        mockMvc.perform(loginRequest(body).with(csrf()))
                .andExpect(jsonError(401, MSG_LOGIN_INVALID))
                .andExpect(unauthenticated());
    }

    @Test
    @DisplayName("FR1-EX14: unparseable JSON login body returns 400 'Malformed request.'")
    void malformedJson() throws Exception {
        mockMvc.perform(loginRequest("{not json").with(csrf()))
                .andExpect(jsonError(400, MSG_MALFORMED))
                .andExpect(unauthenticated());
    }

    @Test
    @DisplayName("NFR2-AC4: login without a CSRF token returns 403 'Invalid or missing CSRF token.' and stays unauthenticated")
    void loginWithoutCsrf() throws Exception {
        mockMvc.perform(loginRequest(loginBody(ALICE_ID, PASSWORD)))
                .andExpect(jsonError(403, MSG_CSRF_INVALID))
                .andExpect(unauthenticated());
    }

    @Test
    @DisplayName("NFR2-AC5: a JSON-logged-in student has exactly one authority, ROLE_STUDENT")
    void loggedInStudentHasOnlyStudentRole() throws Exception {
        mockMvc.perform(loginRequest(loginBody(ALICE_ID, PASSWORD)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(authenticated()
                        .withUsername(ALICE_ID)
                        .withAuthorities(List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));
    }

    @Test
    @DisplayName("FR1-EX11: a successful login changes the session id (session fixation protection)")
    void loginChangesSessionId() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String idBefore = session.getId();

        MvcResult result = mockMvc.perform(loginRequest(loginBody(ALICE_ID, PASSWORD)).with(csrf()).session(session))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNotNull();
        String idAfter = result.getRequest().getSession(false).getId();
        assertThat(idAfter).isNotEqualTo(idBefore);
    }

    // ---------------------------------------------------------------- logout

    @Test
    @DisplayName("FR1-EX3: logout with CSRF returns 204 with an empty body and the session no longer authenticates")
    void logoutInvalidatesSession() throws Exception {
        MockHttpSession session = loginAsAlice(mockMvc);

        mockMvc.perform(post("/api/auth/logout").session(session).with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(jsonError(401, MSG_AUTH_REQUIRED));
    }

    @Test
    @DisplayName("FR1-EX13: logout without a CSRF token returns 403 'Invalid or missing CSRF token.' and the session stays logged in")
    void logoutWithoutCsrf() throws Exception {
        MockHttpSession session = loginAsAlice(mockMvc);

        mockMvc.perform(post("/api/auth/logout").session(session))
                .andExpect(jsonError(403, MSG_CSRF_INVALID));

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(content().json(ALICE_JSON, JsonCompareMode.STRICT));
    }

    // ---------------------------------------------------------------- me

    @Test
    @DisplayName("FR1-EX8: GET /api/auth/me as 410000001 returns exactly Alice's profile")
    void meReturnsProfile() throws Exception {
        mockMvc.perform(get("/api/auth/me").with(user(ALICE_ID).roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json(ALICE_JSON, JsonCompareMode.STRICT));
    }

    @Test
    @DisplayName("FR1-EX19: GET /api/auth/me for a principal with no student row returns 404 'Student not found.'")
    void meForUnknownStudent() throws Exception {
        mockMvc.perform(get("/api/auth/me").with(user("499999999").roles("STUDENT")))
                .andExpect(jsonError(404, MSG_STUDENT_NOT_FOUND));
    }

    // ---------------------------------------------------------------- no hash leakage

    @Test
    @DisplayName("FR1-EX6: login, me and dashboard bodies never contain the password hash and profiles have exactly studentId/name/email")
    void noPasswordHashInBodies() throws Exception {
        MvcResult login = mockMvc.perform(loginRequest(loginBody(ALICE_ID, PASSWORD)).with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(session).isNotNull();

        String loginBody = login.getResponse().getContentAsString();
        String meBody = mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String dashboardBody = mockMvc.perform(get("/api/dashboard").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        for (String body : List.of(loginBody, meBody, dashboardBody)) {
            assertThat(body).doesNotContain("$2").doesNotContain("passwordHash");
        }

        Set<String> profileKeys = Set.of("studentId", "name", "email");
        assertThat(keysOf(objectMapper.readTree(loginBody))).as("login profile keys").isEqualTo(profileKeys);
        assertThat(keysOf(objectMapper.readTree(meBody))).as("me profile keys").isEqualTo(profileKeys);
        JsonNode dashboard = objectMapper.readTree(dashboardBody);
        assertThat(dashboard.has("student")).isTrue();
        assertThat(keysOf(dashboard.get("student"))).as("dashboard student keys").isEqualTo(profileKeys);
    }

    // ---------------------------------------------------------------- helpers

    private static Set<String> keysOf(JsonNode node) {
        Set<String> keys = new TreeSet<>();
        node.fieldNames().forEachRemaining(keys::add);
        return keys;
    }
}
