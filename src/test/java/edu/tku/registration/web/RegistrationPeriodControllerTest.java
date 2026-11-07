package edu.tku.registration.web;

import static edu.tku.registration.support.MutableTestClock.*;
import static edu.tku.registration.web.ApiTestSupport.ALICE_ID;
import static edu.tku.registration.web.ApiTestSupport.MSG_STUDENT_NOT_FOUND;
import static edu.tku.registration.web.ApiTestSupport.jsonError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.tku.registration.domain.Course;
import edu.tku.registration.domain.Semester;
import edu.tku.registration.repository.AuditLogRepository;
import edu.tku.registration.repository.CourseRepository;
import edu.tku.registration.repository.RegistrationPeriodRepository;
import edu.tku.registration.repository.RegistrationRepository;
import edu.tku.registration.repository.SemesterRepository;
import edu.tku.registration.support.MutableTestClock;
import edu.tku.registration.support.PeriodTestData;
import edu.tku.registration.support.PinnedClockConfig;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/** FR7 backend criteria over HTTP (spec FR4-FR5-FR7). Rolled back after each test, Clock pinned and mutable. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(PinnedClockConfig.class)
class RegistrationPeriodControllerTest {

    private static final RequestPostProcessor ALICE = user(ALICE_ID).roles("STUDENT");
    private static final RequestPostProcessor CAROL = user("410000003").roles("STUDENT");
    private static final String PERIOD_115_1 =
            "{\"startAt\":\"2026-08-01T00:00:00\",\"endAt\":\"2026-12-31T23:59:59\",\"open\":true}";
    private static final String PERIOD_115_1_CLOSED =
            "{\"startAt\":\"2026-08-01T00:00:00\",\"endAt\":\"2026-12-31T23:59:59\",\"open\":false}";
    private static final String PERIOD_114_2_CLOSED =
            "{\"startAt\":\"2026-01-15T00:00:00\",\"endAt\":\"2026-02-28T23:59:59\",\"open\":false}";

    @Autowired private MockMvc mockMvc;
    @Autowired private MutableTestClock clock;
    @Autowired private CourseRepository courseRepository;
    @Autowired private SemesterRepository semesterRepository;
    @Autowired private RegistrationPeriodRepository periodRepository;
    @Autowired private RegistrationRepository registrationRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private EntityManager entityManager;
    private final ObjectMapper mapper = new ObjectMapper();

    private Long cs101Id;

    @BeforeEach
    void setUp() {
        clock.set(T_OPEN);
        cs101Id = idOf("CS101", "115-1");
    }

    private Long idOf(String code, String semester) {
        return courseRepository.findByCourseCodeAndSemester_Code(code, semester).orElseThrow().getId();
    }

    private ResultActions register(RequestPostProcessor who, Long courseId) throws Exception {
        return mockMvc.perform(post("/api/registrations").with(who).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"courseId\":" + courseId + "}"));
    }

    private Course fresh(Long id) {
        entityManager.flush();
        entityManager.clear();
        return courseRepository.findById(id).orElseThrow();
    }

    private long regs() {
        entityManager.flush();
        return registrationRepository.count();
    }

    private long audits() {
        entityManager.flush();
        return auditLogRepository.count();
    }

    private JsonNode json(ResultActions r) throws Exception {
        return mapper.readTree(r.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static Set<String> keys(JsonNode n) {
        Set<String> keys = new TreeSet<>();
        for (Iterator<String> it = n.fieldNames(); it.hasNext();) {
            keys.add(it.next());
        }
        return keys;
    }

    @ParameterizedTest(name = "FR7-AC1a: at {0} registering CS101 returns 201")
    @ValueSource(strings = {T_START, T_OPEN, T_END, T_END_MS})
    @DisplayName("FR7-AC1a: inside the period (inclusive bounds, whole end second) POST returns 201")
    void insidePeriod(String instant) throws Exception {
        clock.set(instant);
        register(ALICE, cs101Id).andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Successfully registered for CS101."));
    }

    @ParameterizedTest(name = "FR7-AC1b: at {0} registering CS101 returns 409 and writes nothing")
    @ValueSource(strings = {T_BEFORE_START, T_AFTER_END, T_LATE})
    @DisplayName("FR7-AC1b: outside the period POST returns 409 'Registration is closed for semester 115-1.' and writes nothing")
    void outsidePeriod(String instant) throws Exception {
        Long version = fresh(cs101Id).getVersion();
        long regsBefore = regs();
        long auditsBefore = audits();
        clock.set(instant);

        register(ALICE, cs101Id).andExpect(jsonError(409, "Registration is closed for semester 115-1."));

        Course c = fresh(cs101Id);
        assertThat(c.getAvailableSeats()).isEqualTo(50);
        assertThat(c.getVersion()).isEqualTo(version);
        assertThat(regs()).isEqualTo(regsBefore);
        assertThat(audits()).isEqualTo(auditsBefore);
    }

    @Test
    @DisplayName("FR7-AC1c: the course's own semester decides: CS102 closed at T_OPEN; at T_SPRING CS102 opens and CS101 closes")
    void ownSemesterDecides() throws Exception {
        Long cs102 = idOf("CS102", "114-2");
        register(ALICE, cs102).andExpect(jsonError(409, "Registration is closed for semester 114-2."));

        clock.set(T_SPRING);
        register(ALICE, cs102).andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Successfully registered for CS102."))
                .andExpect(jsonPath("$.availableSeats").value(44));
        register(ALICE, cs101Id).andExpect(jsonError(409, "Registration is closed for semester 115-1."));
    }

    @Test
    @DisplayName("FR7-EX1: a semester without a RegistrationPeriod is closed at any clock value and nothing is written")
    void noPeriodMeansClosed() throws Exception {
        Semester s = PeriodTestData.semester(semesterRepository, periodRepository, "NOPD-1", null, null);
        Course np = PeriodTestData.course(courseRepository, s, "NP101", 5, 5);
        long regsBefore = regs();
        long auditsBefore = audits();

        for (String t : new String[] {T_OPEN, T_LATE, T_SPRING}) {
            clock.set(t);
            register(ALICE, np.getId()).andExpect(jsonError(409, "Registration is closed for semester NOPD-1."));
        }

        assertThat(fresh(np.getId()).getAvailableSeats()).isEqualTo(5);
        assertThat(regs()).isEqualTo(regsBefore);
        assertThat(audits()).isEqualTo(auditsBefore);
    }

    @Test
    @DisplayName("FR7-EX2: past and future periods are closed at T_OPEN, and open when the clock is inside them")
    void pastAndFuture() throws Exception {
        Semester past = PeriodTestData.semester(semesterRepository, periodRepository, "PAST-1",
                LocalDateTime.parse("2025-01-01T00:00:00"), LocalDateTime.parse("2025-01-31T23:59:59"));
        Semester fut = PeriodTestData.semester(semesterRepository, periodRepository, "FUT-1",
                LocalDateTime.parse("2027-01-01T00:00:00"), LocalDateTime.parse("2027-01-31T23:59:59"));
        Long pastCourse = PeriodTestData.course(courseRepository, past, "PA101", 5, 5).getId();
        Long futCourse = PeriodTestData.course(courseRepository, fut, "FU101", 5, 5).getId();
        long regsBefore = regs();
        long auditsBefore = audits();

        register(ALICE, pastCourse).andExpect(jsonError(409, "Registration is closed for semester PAST-1."));
        register(ALICE, futCourse).andExpect(jsonError(409, "Registration is closed for semester FUT-1."));
        assertThat(regs()).isEqualTo(regsBefore);
        assertThat(audits()).isEqualTo(auditsBefore);

        clock.set("2025-01-15T12:00:00+08:00");
        register(ALICE, pastCourse).andExpect(status().isCreated());
        clock.set("2027-01-15T12:00:00+08:00");
        register(ALICE, futCourse).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("FR7-EX3: check order student, course, period, duplicate, full")
    void checkOrder() throws Exception {
        Long ma101 = idOf("MA101", "115-1");
        clock.set(T_LATE);
        register(CAROL, ma101).andExpect(jsonError(409, "Registration is closed for semester 115-1."));
        register(ALICE, 999999L).andExpect(jsonError(404, "Course not found."));
        register(user("499999999").roles("STUDENT"), cs101Id).andExpect(jsonError(404, MSG_STUDENT_NOT_FOUND));

        clock.set(T_OPEN);
        register(CAROL, ma101).andExpect(jsonError(409, "You are already registered for MA101."));
    }

    @Test
    @DisplayName("FR7-EX5: GET /api/courses carries registrationPeriod for the selected semester; other keys unchanged")
    void listingCarriesPeriod() throws Exception {
        String body = mockMvc.perform(get("/api/courses").with(ALICE)).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString();
        JsonNode l = mapper.readTree(body);
        assertThat(keys(l)).containsExactlyInAnyOrder("semester", "registrationPeriod", "courses");
        assertThat(keys(l.get("semester"))).containsExactlyInAnyOrder("code", "name", "current");
        for (JsonNode c : l.get("courses")) {
            assertThat(keys(c)).containsExactlyInAnyOrder(
                    "id", "code", "name", "credits", "instructor", "capacity", "availableSeats", "registered");
        }
        assertThat(l.get("registrationPeriod")).isEqualTo(mapper.readTree(PERIOD_115_1));

        clock.set(T_LATE);
        String late = mockMvc.perform(get("/api/courses").with(ALICE)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(late).get("registrationPeriod"))
                .isEqualTo(mapper.readTree(PERIOD_115_1_CLOSED));

        clock.set(T_OPEN);
        String spring = mockMvc.perform(get("/api/courses").param("semester", "114-2").with(ALICE))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(spring).get("registrationPeriod"))
                .isEqualTo(mapper.readTree(PERIOD_114_2_CLOSED));
    }

    @Test
    @DisplayName("FR7-EX6: period-rejected attempts are never audited")
    void rejectedNotAudited() throws Exception {
        Semester s = PeriodTestData.semester(semesterRepository, periodRepository, "NOPD-1", null, null);
        Long np = PeriodTestData.course(courseRepository, s, "NP101", 5, 5).getId();
        Semester fut = PeriodTestData.semester(semesterRepository, periodRepository, "FUT-1",
                LocalDateTime.parse("2027-01-01T00:00:00"), LocalDateTime.parse("2027-01-31T23:59:59"));
        Long futCourse = PeriodTestData.course(courseRepository, fut, "FU101", 5, 5).getId();
        Long ma101 = idOf("MA101", "115-1");
        long before = audits();

        for (String t : new String[] {T_BEFORE_START, T_AFTER_END, T_LATE}) {
            clock.set(t);
            register(ALICE, cs101Id).andExpect(status().isConflict());
            register(ALICE, np).andExpect(status().isConflict());
            register(CAROL, ma101).andExpect(status().isConflict());
        }
        clock.set(T_OPEN);
        register(ALICE, futCourse).andExpect(status().isConflict());

        assertThat(audits()).isEqualTo(before);
    }

    @Test
    @DisplayName("FR7-EX7: the dashboard uses the same rule: open at T_END_MS, closed at T_AFTER_END")
    void dashboardSameRule() throws Exception {
        clock.set(T_END_MS);
        mockMvc.perform(get("/api/dashboard").with(ALICE)).andExpect(status().isOk())
                .andExpect(jsonPath("$.currentSemester.registrationOpen").value(true));
        clock.set(T_AFTER_END);
        mockMvc.perform(get("/api/dashboard").with(ALICE)).andExpect(status().isOk())
                .andExpect(jsonPath("$.currentSemester.registrationOpen").value(false));
    }

    @Test
    @DisplayName("FR7-AC3a: existing registrations stay viewable after the period ends")
    void viewableAfterPeriod() throws Exception {
        clock.set(T_LATE);
        mockMvc.perform(get("/api/registrations").with(CAROL)).andExpect(status().isOk())
                .andExpect(jsonPath("$.courses.length()").value(1))
                .andExpect(jsonPath("$.courses[0].code").value("MA101"))
                .andExpect(jsonPath("$.registrationPeriod.open").value(false));

        JsonNode l = json(mockMvc.perform(get("/api/courses").with(CAROL)));
        boolean registered = false;
        for (JsonNode c : l.get("courses")) {
            if ("MA101".equals(c.get("code").asText())) {
                registered = c.get("registered").asBoolean();
            }
        }
        assertThat(registered).isTrue();
    }
}
