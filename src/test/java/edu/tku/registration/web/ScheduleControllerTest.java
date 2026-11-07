package edu.tku.registration.web;

import static edu.tku.registration.support.MutableTestClock.*;
import static edu.tku.registration.web.ApiTestSupport.ALICE_ID;
import static edu.tku.registration.web.ApiTestSupport.MSG_AUTH_REQUIRED;
import static edu.tku.registration.web.ApiTestSupport.MSG_STUDENT_NOT_FOUND;
import static edu.tku.registration.web.ApiTestSupport.jsonError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.tku.registration.repository.CourseRepository;
import edu.tku.registration.support.MutableTestClock;
import edu.tku.registration.support.PinnedClockConfig;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/** GET /api/registrations (spec FR5 backend criteria). Rolled back after each test, Clock pinned and mutable. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(PinnedClockConfig.class)
class ScheduleControllerTest {

    private static final RequestPostProcessor ALICE = user(ALICE_ID).roles("STUDENT");
    private static final RequestPostProcessor CAROL = user("410000003").roles("STUDENT");
    private static final String SEM_115_1 = "{\"code\":\"115-1\",\"name\":\"2026 Fall\",\"current\":true}";
    private static final String SEM_114_2 = "{\"code\":\"114-2\",\"name\":\"2026 Spring\",\"current\":false}";

    @Autowired private MockMvc mockMvc;
    @Autowired private MutableTestClock clock;
    @Autowired private CourseRepository courseRepository;
    private final ObjectMapper mapper = new ObjectMapper();

    private Long cs101Id;
    private Long cs201Id;

    @BeforeEach
    void setUp() {
        clock.set(T_OPEN);
        cs101Id = idOf("CS101", "115-1");
        cs201Id = idOf("CS201", "115-1");
    }

    private Long idOf(String code, String semester) {
        return courseRepository.findByCourseCodeAndSemester_Code(code, semester).orElseThrow().getId();
    }

    private void register(RequestPostProcessor who, Long courseId) throws Exception {
        mockMvc.perform(post("/api/registrations").with(who).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"courseId\":" + courseId + "}"))
                .andExpect(status().isCreated());
    }

    private JsonNode schedule(RequestPostProcessor who, String... params) throws Exception {
        var req = get("/api/registrations").with(who);
        for (int i = 0; i < params.length; i += 2) {
            req = req.param(params[i], params[i + 1]);
        }
        String body = mockMvc.perform(req).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body);
    }

    private static Set<String> keys(JsonNode n) {
        Set<String> keys = new TreeSet<>();
        for (Iterator<String> it = n.fieldNames(); it.hasNext();) {
            keys.add(it.next());
        }
        return keys;
    }

    private static List<String> codes(JsonNode s) {
        List<String> out = new ArrayList<>();
        for (JsonNode c : s.get("courses")) {
            out.add(c.get("code").asText());
        }
        return out;
    }

    private static void collectFieldNames(JsonNode n, Set<String> out) {
        if (n.isObject()) {
            n.fieldNames().forEachRemaining(out::add);
            n.elements().forEachRemaining(c -> collectFieldNames(c, out));
        } else if (n.isArray()) {
            n.elements().forEachRemaining(c -> collectFieldNames(c, out));
        }
    }

    @Test
    @DisplayName("FR5-AC1: Carol's schedule has exactly the five contract keys and exactly the MA101 row with the contract keys")
    void carolSchedule() throws Exception {
        JsonNode s = schedule(CAROL);
        assertThat(keys(s)).containsExactlyInAnyOrder(
                "semester", "semesters", "registrationPeriod", "courses", "totalCredits");
        assertThat(s.get("semester")).isEqualTo(mapper.readTree(SEM_115_1));
        assertThat(s.get("courses")).hasSize(1);
        JsonNode c = s.get("courses").get(0);
        assertThat(keys(c)).containsExactlyInAnyOrder("registrationId", "courseId", "code", "name", "credits",
                "instructor", "semesterCode", "registeredAt");
        assertThat(c.get("code").asText()).isEqualTo("MA101");
        assertThat(c.get("name").asText()).isEqualTo("Calculus I");
        assertThat(c.get("credits").asInt()).isEqualTo(4);
        assertThat(c.get("instructor").asText()).isEqualTo("Prof. Huang");
        assertThat(c.get("semesterCode").asText()).isEqualTo("115-1");
        assertThat(c.get("registeredAt").asText()).isEqualTo("2026-08-10T10:00:00");
        assertThat(c.get("courseId").asLong()).isEqualTo(idOf("MA101", "115-1"));
        assertThat(c.get("registrationId").isNumber()).isTrue();

        JsonNode alice = schedule(ALICE);
        assertThat(alice.get("courses")).isEmpty();
        assertThat(alice.get("courses").isArray()).isTrue();
    }

    @Test
    @DisplayName("FR5-AC2: totalCredits is the sum of the listed credits: 6 after CS101+CS201, 3 after dropping CS201, 4 for Carol, 0 for none")
    void totalCredits() throws Exception {
        JsonNode none = schedule(ALICE);
        assertThat(none.get("totalCredits").isNumber()).isTrue();
        assertThat(none.get("totalCredits").asInt()).isZero();

        register(ALICE, cs101Id);
        register(ALICE, cs201Id);
        JsonNode two = schedule(ALICE);
        assertThat(two.get("courses")).hasSize(2);
        assertThat(two.get("totalCredits").asInt()).isEqualTo(6);

        mockMvc.perform(delete("/api/registrations/" + cs201Id).with(ALICE).with(csrf()))
                .andExpect(status().isOk());
        assertThat(schedule(ALICE).get("totalCredits").asInt()).isEqualTo(3);
        assertThat(schedule(CAROL).get("totalCredits").asInt()).isEqualTo(4);
    }

    @Test
    @DisplayName("FR5-EX1: semester selection: default is 115-1, ?semester=114-2 selects 114-2, unknown is 404, blank is absent")
    void semesterSelection() throws Exception {
        clock.set(T_SPRING);
        register(ALICE, idOf("CS102", "114-2"));
        clock.set(T_OPEN);
        register(ALICE, cs101Id);

        for (String t : new String[] {T_OPEN, T_LATE}) {
            clock.set(t);
            JsonNode def = schedule(ALICE);
            assertThat(codes(def)).containsExactly("CS101");
            assertThat(def.get("totalCredits").asInt()).isEqualTo(3);
        }
        JsonNode spring = schedule(ALICE, "semester", "114-2");
        assertThat(spring.get("semester").get("code").asText()).isEqualTo("114-2");
        assertThat(codes(spring)).containsExactly("CS102");
        assertThat(spring.get("totalCredits").asInt()).isEqualTo(3);

        mockMvc.perform(get("/api/registrations").param("semester", "999-9").with(ALICE))
                .andExpect(jsonError(404, "Semester not found."));
        assertThat(codes(schedule(ALICE, "semester", ""))).containsExactly("CS101");
        assertThat(codes(schedule(ALICE, "semester", "   "))).containsExactly("CS101");
    }

    @Test
    @DisplayName("FR5-EX3: registrationPeriod is the selected semester's period status")
    void periodStatus() throws Exception {
        String open = "{\"startAt\":\"2026-08-01T00:00:00\",\"endAt\":\"2026-12-31T23:59:59\",\"open\":true}";
        String closed = open.replace("true", "false");
        assertThat(schedule(ALICE).get("registrationPeriod")).isEqualTo(mapper.readTree(open));
        clock.set(T_LATE);
        assertThat(schedule(ALICE).get("registrationPeriod")).isEqualTo(mapper.readTree(closed));
        clock.set(T_OPEN);
        JsonNode spring = schedule(ALICE, "semester", "114-2");
        assertThat(spring.get("registrationPeriod").get("open").asBoolean()).isFalse();
        assertThat(spring.get("registrationPeriod").get("startAt").asText()).isEqualTo("2026-01-15T00:00:00");
        assertThat(spring.get("registrationPeriod").get("endAt").asText()).isEqualTo("2026-02-28T23:59:59");
    }

    @Test
    @DisplayName("FR5-EX4: semesters lists all semesters ordered by code descending")
    void semestersList() throws Exception {
        String body = mockMvc.perform(get("/api/registrations").with(ALICE)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(body).get("semesters"))
                .isEqualTo(mapper.readTree("[" + SEM_115_1 + "," + SEM_114_2 + "]"));
    }

    @Test
    @DisplayName("FR5-EX5: only the student's own rows, ordered by code ascending, registeredAt in the fixed format")
    void isolationAndOrder() throws Exception {
        register(ALICE, cs201Id);
        register(ALICE, cs101Id);
        JsonNode s = schedule(ALICE);
        assertThat(codes(s)).containsExactly("CS101", "CS201");
        assertThat(codes(s)).doesNotContain("MA101");
        for (JsonNode c : s.get("courses")) {
            assertThat(c.get("registeredAt").asText()).isEqualTo("2026-09-01T09:00:00");
        }
    }

    @Test
    @DisplayName("FR5-EX6: the JSON never contains version, passwordHash, student, email or capacity")
    void noEntityLeakage() throws Exception {
        register(ALICE, cs101Id);
        Set<String> names = new TreeSet<>();
        collectFieldNames(schedule(ALICE), names);
        collectFieldNames(schedule(CAROL), names);
        assertThat(names).doesNotContain("version", "passwordHash", "student", "email", "capacity");
    }

    @Test
    @DisplayName("FR5-EX7: anonymous GET gives 401, an unknown student gives 404 'Student not found.'")
    void security() throws Exception {
        mockMvc.perform(get("/api/registrations")).andExpect(jsonError(401, MSG_AUTH_REQUIRED));
        mockMvc.perform(get("/api/registrations").param("semester", "114-2"))
                .andExpect(jsonError(401, MSG_AUTH_REQUIRED));
        mockMvc.perform(get("/api/registrations").with(user("499999999").roles("STUDENT")))
                .andExpect(jsonError(404, MSG_STUDENT_NOT_FOUND));
    }
}
