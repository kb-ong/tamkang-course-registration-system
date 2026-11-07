package edu.tku.registration.web;

import static edu.tku.registration.web.ApiTestSupport.ALICE_ID;
import static edu.tku.registration.web.ApiTestSupport.MSG_AUTH_REQUIRED;
import static edu.tku.registration.web.ApiTestSupport.jsonError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** GET /api/semesters and GET /api/courses against the unchanged seed data (read-only). */
@SpringBootTest
@AutoConfigureMockMvc
class CourseControllerTest {

    private static final String SEM_115_1 = "{\"code\":\"115-1\",\"name\":\"2026 Fall\",\"current\":true}";
    private static final String SEM_114_2 = "{\"code\":\"114-2\",\"name\":\"2026 Spring\",\"current\":false}";

    private static final RequestPostProcessor ALICE = user(ALICE_ID).roles("STUDENT");
    private static final RequestPostProcessor CAROL = user("410000003").roles("STUDENT");

    @Autowired
    private MockMvc mockMvc;
    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode listing(RequestPostProcessor who, String... params) throws Exception {
        var req = get("/api/courses").with(who);
        for (int i = 0; i < params.length; i += 2) {
            req.param(params[i], params[i + 1]);
        }
        String body = mockMvc.perform(req)
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body);
    }

    private static List<String> codes(JsonNode listing) {
        List<String> out = new ArrayList<>();
        listing.get("courses").forEach(c -> out.add(c.get("code").asText()));
        return out;
    }

    private static JsonNode course(JsonNode listing, String code) {
        for (JsonNode c : listing.get("courses")) {
            if (code.equals(c.get("code").asText())) {
                return c;
            }
        }
        throw new AssertionError("course " + code + " not in listing");
    }

    private static Set<String> keys(JsonNode node) {
        Set<String> keys = new TreeSet<>();
        for (Iterator<String> it = node.fieldNames(); it.hasNext();) {
            keys.add(it.next());
        }
        return keys;
    }

    @Test
    @DisplayName("FR2-AC1a: default listing is the current semester 115-1 with CS101, CS201, MA101 in order")
    void defaultListing() throws Exception {
        JsonNode l = listing(ALICE);
        assertThat(l.get("semester").toString()).isEqualTo(SEM_115_1);
        assertThat(codes(l)).containsExactly("CS101", "CS201", "MA101");
    }

    @Test
    @DisplayName("FR2-AC1b: search by code is case-insensitive substring and trims the query")
    void searchByCode() throws Exception {
        assertThat(codes(listing(ALICE, "q", "CS101"))).containsExactly("CS101");
        assertThat(codes(listing(ALICE, "q", "cs"))).containsExactly("CS101", "CS201");
        assertThat(codes(listing(ALICE, "q", " ma10 "))).containsExactly("MA101");
    }

    @Test
    @DisplayName("FR2-AC1c: search by name is case-insensitive substring and ignores other semesters")
    void searchByName() throws Exception {
        assertThat(codes(listing(ALICE, "q", "programming"))).containsExactly("CS101");
        assertThat(codes(listing(ALICE, "q", "DATA"))).containsExactly("CS201");
        assertThat(codes(listing(ALICE, "q", "calc"))).containsExactly("MA101");
    }

    @Test
    @DisplayName("FR2-AC2: each course object has exactly the contract keys and the seed values")
    void courseShape() throws Exception {
        JsonNode l = listing(ALICE);
        Set<String> expected = new TreeSet<>(List.of(
                "id", "code", "name", "credits", "instructor", "capacity", "availableSeats", "registered"));
        for (JsonNode c : l.get("courses")) {
            assertThat(keys(c)).isEqualTo(expected);
        }
        JsonNode cs101 = course(l, "CS101");
        assertThat(cs101.get("id").isNumber()).isTrue();
        assertThat(cs101.get("name").asText()).isEqualTo("Introduction to Programming");
        assertThat(cs101.get("credits").asInt()).isEqualTo(3);
        assertThat(cs101.get("instructor").asText()).isEqualTo("Prof. Lee");
        assertThat(cs101.get("capacity").asInt()).isEqualTo(50);
        assertThat(cs101.get("availableSeats").asInt()).isEqualTo(50);
        assertThat(cs101.get("registered").isBoolean()).isTrue();
        assertThat(cs101.get("registered").asBoolean()).isFalse();
        JsonNode ma101 = course(l, "MA101");
        assertThat(ma101.get("credits").asInt()).isEqualTo(4);
        assertThat(ma101.get("instructor").asText()).isEqualTo("Prof. Huang");
        assertThat(ma101.get("capacity").asInt()).isEqualTo(1);
        assertThat(ma101.get("availableSeats").asInt()).isEqualTo(0);
    }

    @Test
    @DisplayName("FR2-AC3a: semester=114-2 lists only CS102; the 115-1 listings never contain CS102")
    void otherSemester() throws Exception {
        JsonNode l = listing(ALICE, "semester", "114-2");
        assertThat(l.get("semester").get("code").asText()).isEqualTo("114-2");
        assertThat(codes(l)).containsExactly("CS102");
        JsonNode cs102 = course(l, "CS102");
        assertThat(cs102.get("credits").asInt()).isEqualTo(3);
        assertThat(cs102.get("availableSeats").asInt()).isEqualTo(45);
        assertThat(codes(listing(ALICE))).doesNotContain("CS102");
        assertThat(codes(listing(ALICE, "semester", "115-1"))).doesNotContain("CS102");
    }

    @Test
    @DisplayName("FR2-AC3b: search is scoped to the selected semester")
    void searchScopedToSemester() throws Exception {
        assertThat(codes(listing(ALICE, "semester", "114-2", "q", "cs"))).containsExactly("CS102");
        JsonNode other = listing(ALICE, "semester", "115-1", "q", "object");
        assertThat(other.get("courses").isArray()).isTrue();
        assertThat(other.get("courses")).isEmpty();
    }

    @Test
    @DisplayName("FR2-EX1: absent, empty or blank q returns the full semester list")
    void blankQueryIsNoFilter() throws Exception {
        List<String> all = List.of("CS101", "CS201", "MA101");
        assertThat(codes(listing(ALICE))).isEqualTo(all);
        assertThat(codes(listing(ALICE, "q", ""))).isEqualTo(all);
        assertThat(codes(listing(ALICE, "q", "   "))).isEqualTo(all);
    }

    @Test
    @DisplayName("FR2-EX2: q=zzz returns 200 with an empty list and the semester still set")
    void noMatch() throws Exception {
        mockMvc.perform(get("/api/courses").param("q", "zzz").with(ALICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courses").isArray())
                .andExpect(jsonPath("$.courses").isEmpty())
                .andExpect(jsonPath("$.semester.code").value("115-1"));
    }

    @Test
    @DisplayName("FR2-EX3: % and _ in q are literal characters, not wildcards")
    void wildcardsAreLiteral() throws Exception {
        assertThat(listing(ALICE, "q", "%").get("courses")).isEmpty();
        assertThat(listing(ALICE, "q", "_").get("courses")).isEmpty();
    }

    @Test
    @DisplayName("FR2-EX4: unknown semester returns 404 'Semester not found.'")
    void unknownSemester() throws Exception {
        mockMvc.perform(get("/api/courses").param("semester", "999-9").with(ALICE))
                .andExpect(jsonError(404, "Semester not found."));
    }

    @Test
    @DisplayName("FR2-EX6: GET /api/semesters returns both semesters ordered by code descending")
    void semesters() throws Exception {
        mockMvc.perform(get("/api/semesters").with(ALICE))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("[" + SEM_115_1 + "," + SEM_114_2 + "]", JsonCompareMode.STRICT));
    }

    @Test
    @DisplayName("FR2-EX7: the registered flag is per student")
    void registeredIsPerStudent() throws Exception {
        JsonNode carol = listing(CAROL);
        assertThat(course(carol, "MA101").get("registered").asBoolean()).isTrue();
        assertThat(course(carol, "CS101").get("registered").asBoolean()).isFalse();
        assertThat(course(carol, "CS201").get("registered").asBoolean()).isFalse();
        JsonNode alice = listing(ALICE);
        for (JsonNode c : alice.get("courses")) {
            assertThat(c.get("registered").asBoolean()).as(c.get("code").asText()).isFalse();
        }
        JsonNode carolSpring = listing(CAROL, "semester", "114-2");
        assertThat(course(carolSpring, "CS102").get("registered").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("FR2-EX8: anonymous GET /api/semesters and /api/courses return 401 'Authentication required.'")
    void anonymousIs401() throws Exception {
        mockMvc.perform(get("/api/semesters")).andExpect(jsonError(401, MSG_AUTH_REQUIRED));
        mockMvc.perform(get("/api/courses").param("q", "cs")).andExpect(jsonError(401, MSG_AUTH_REQUIRED));
    }

    @Test
    @DisplayName("FR2-EX9: course objects leak no entity fields and are sorted by code ascending")
    void noEntityLeakageAndSorted() throws Exception {
        JsonNode l = listing(ALICE);
        for (JsonNode c : l.get("courses")) {
            assertThat(c.has("version")).isFalse();
            assertThat(c.has("semester")).isFalse();
        }
        List<String> codes = codes(l);
        assertThat(codes).isSorted();
    }
}
