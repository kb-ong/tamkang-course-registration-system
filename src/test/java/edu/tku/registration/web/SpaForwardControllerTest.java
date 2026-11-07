package edu.tku.registration.web;

import static edu.tku.registration.web.ApiTestSupport.ALICE_ID;
import static edu.tku.registration.web.ApiTestSupport.MSG_NOT_FOUND;
import static edu.tku.registration.web.ApiTestSupport.jsonError;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** SPA shell forwarding and unknown API paths (spec FR1-login rev 2, §3.4 SpaForwardController / ApiExceptionHandler). */
@SpringBootTest
@AutoConfigureMockMvc
class SpaForwardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("FR1-EX7: GET / anonymously is forwarded to /index.html")
    void rootAnonymous() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    @DisplayName("FR1-EX7: GET / as an authenticated student is forwarded to /index.html")
    void rootAuthenticated() throws Exception {
        mockMvc.perform(get("/").with(user(ALICE_ID).roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @ParameterizedTest(name = "FR1-EX16: GET {0}")
    @ValueSource(strings = {"/login", "/dashboard", "/courses/123"})
    @DisplayName("FR1-EX16: anonymous GET of an SPA route is forwarded to /index.html")
    void spaRoutesForwarded(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @ParameterizedTest(name = "FR1-EX16: anonymous GET {0}")
    @ValueSource(strings = {"/api", "/api/anything"})
    @DisplayName("FR1-EX16: anonymous GET /api and /api/anything are never forwarded to /index.html")
    void apiPathsNotForwardedAnonymous(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(forwardedUrl(null));
    }

    @ParameterizedTest(name = "FR1-EX16: authenticated GET {0}")
    @ValueSource(strings = {"/api", "/api/anything"})
    @DisplayName("FR1-EX16: authenticated GET /api and /api/anything are never forwarded to /index.html")
    void apiPathsNotForwardedAuthenticated(String path) throws Exception {
        mockMvc.perform(get(path).with(user(ALICE_ID).roles("STUDENT")))
                .andExpect(forwardedUrl(null));
    }

    @Test
    @DisplayName("FR1-EX15: authenticated GET /api/does-not-exist returns 404 'Not found.'")
    void unknownApiPathIs404() throws Exception {
        mockMvc.perform(get("/api/does-not-exist").with(user(ALICE_ID).roles("STUDENT")))
                .andExpect(jsonError(404, MSG_NOT_FOUND))
                .andExpect(forwardedUrl(null));
    }
}
