package edu.tku.registration.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Shared helpers for the JSON API tests (spec FR1-login revision 2). */
public final class ApiTestSupport {

    public static final String ALICE_ID = "410000001";
    public static final String PASSWORD = "password123";
    public static final String ALICE_JSON =
            "{\"studentId\":\"410000001\",\"name\":\"Alice Chen\",\"email\":\"410000001@o365.tku.edu.tw\"}";
    public static final String BOB_JSON =
            "{\"studentId\":\"410000002\",\"name\":\"Bob Lin\",\"email\":\"410000002@o365.tku.edu.tw\"}";

    public static final String MSG_LOGIN_INVALID = "Invalid student ID or password.";
    public static final String MSG_AUTH_REQUIRED = "Authentication required.";
    public static final String MSG_CSRF_INVALID = "Invalid or missing CSRF token.";
    public static final String MSG_MALFORMED = "Malformed request.";
    public static final String MSG_STUDENT_NOT_FOUND = "Student not found.";
    public static final String MSG_NOT_FOUND = "Not found.";

    private ApiTestSupport() {
    }

    /** JSON body for a login request. */
    public static String loginBody(String studentId, String password) {
        return "{\"studentId\":\"" + studentId + "\",\"password\":\"" + password + "\"}";
    }

    /** POST /api/auth/login with a JSON body, without any CSRF token. */
    public static MockHttpServletRequestBuilder loginRequest(String jsonBody) {
        return post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(jsonBody);
    }

    /**
     * Spec "JSON error X": the status, a Content-Type compatible with application/json,
     * and a body that is exactly {"message":"X"}.
     */
    public static ResultMatcher jsonError(int expectedStatus, String message) {
        return ResultMatcher.matchAll(
                status().is(expectedStatus),
                content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON),
                content().json("{\"message\":" + quote(message) + "}", JsonCompareMode.STRICT));
    }

    /** Performs a successful login as Alice (csrf() post-processor) and returns the session it created. */
    public static MockHttpSession loginAsAlice(MockMvc mockMvc) throws Exception {
        MvcResult result = mockMvc.perform(loginRequest(loginBody(ALICE_ID, PASSWORD)).with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        if (session == null) {
            throw new AssertionError("successful login did not create an HTTP session");
        }
        return session;
    }

    /** All cookies with the given name set on the response, in the order they were added. */
    public static List<Cookie> cookiesNamed(MockHttpServletResponse response, String name) {
        return Arrays.stream(response.getCookies()).filter(c -> name.equals(c.getName())).toList();
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
