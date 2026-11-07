package edu.tku.registration.web;

import static edu.tku.registration.web.ApiTestSupport.ALICE_ID;
import static edu.tku.registration.web.ApiTestSupport.ALICE_JSON;
import static edu.tku.registration.web.ApiTestSupport.BOB_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

/** GET /api/dashboard with the Clock fixed at 2026-09-01T09:00 Asia/Taipei (spec FR1-AC3). */
@SpringBootTest
@AutoConfigureMockMvc
class DashboardControllerTest {

    private static final String SEMESTER_115_1_OPEN =
            "{\"code\":\"115-1\",\"name\":\"2026 Fall\","
                    + "\"registrationStartAt\":\"2026-08-01T00:00:00\","
                    + "\"registrationEndAt\":\"2026-12-31T23:59:59\","
                    + "\"registrationOpen\":true}";

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedTestClock() {
            ZoneId taipei = ZoneId.of("Asia/Taipei");
            return Clock.fixed(OffsetDateTime.parse("2026-09-01T09:00:00+08:00").toInstant(), taipei);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("FR1-AC3: GET /api/dashboard as 410000001 returns exactly Alice's profile and the open 115-1 semester")
    void dashboardForAlice() throws Exception {
        mockMvc.perform(get("/api/dashboard").with(user(ALICE_ID).roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json(
                        "{\"student\":" + ALICE_JSON + ",\"currentSemester\":" + SEMESTER_115_1_OPEN + "}",
                        JsonCompareMode.STRICT));
    }

    @Test
    @DisplayName("FR1-AC3: GET /api/dashboard follows the authenticated principal (410000002 gets Bob Lin's profile)")
    void dashboardFollowsPrincipal() throws Exception {
        mockMvc.perform(get("/api/dashboard").with(user("410000002").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(content().json(
                        "{\"student\":" + BOB_JSON + ",\"currentSemester\":" + SEMESTER_115_1_OPEN + "}",
                        JsonCompareMode.STRICT));
    }
}
