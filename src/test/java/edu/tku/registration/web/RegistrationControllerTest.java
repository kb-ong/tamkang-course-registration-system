package edu.tku.registration.web;

import static edu.tku.registration.web.ApiTestSupport.ALICE_ID;
import static edu.tku.registration.web.ApiTestSupport.MSG_CSRF_INVALID;
import static edu.tku.registration.web.ApiTestSupport.MSG_MALFORMED;
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
import edu.tku.registration.domain.AuditAction;
import edu.tku.registration.domain.AuditLog;
import edu.tku.registration.domain.Course;
import edu.tku.registration.domain.Registration;
import edu.tku.registration.repository.AuditLogRepository;
import edu.tku.registration.repository.CourseRepository;
import edu.tku.registration.repository.RegistrationRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/**
 * POST /api/registrations (spec FR3). Rolled back after each test. The Clock is a mutable
 * fixed clock (default 2026-09-01T09:00+08:00). No real CSRF cookie is needed here, so csrf() is fine.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RegistrationControllerTest {

    static class MutableClock extends Clock {
        private volatile Instant instant;
        private final ZoneId zone = ZoneId.of("Asia/Taipei");

        void set(String offsetDateTime) {
            this.instant = OffsetDateTime.parse(offsetDateTime).toInstant();
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId z) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MutableClock mutableTestClock() {
            MutableClock c = new MutableClock();
            c.set("2026-09-01T09:00:00+08:00");
            return c;
        }
    }

    private static final RequestPostProcessor ALICE = user(ALICE_ID).roles("STUDENT");
    private static final RequestPostProcessor BOB = user("410000002").roles("STUDENT");
    private static final RequestPostProcessor CAROL = user("410000003").roles("STUDENT");
    private static final LocalDateTime FIXED = LocalDateTime.of(2026, 9, 1, 9, 0, 0);

    @Autowired private MockMvc mockMvc;
    @Autowired private MutableClock clock;
    @Autowired private CourseRepository courseRepository;
    @Autowired private RegistrationRepository registrationRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private EntityManager entityManager;
    private final ObjectMapper mapper = new ObjectMapper();

    private Long cs101Id;

    @BeforeEach
    void setUp() {
        clock.set("2026-09-01T09:00:00+08:00");
        cs101Id = idOf("CS101", "115-1");
    }

    private Long idOf(String code, String semester) {
        return courseRepository.findByCourseCodeAndSemester_Code(code, semester).orElseThrow().getId();
    }

    private ResultActions postJson(RequestPostProcessor who, String body) throws Exception {
        return mockMvc.perform(post("/api/registrations").with(who).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions register(RequestPostProcessor who, Long courseId) throws Exception {
        return postJson(who, "{\"courseId\":" + courseId + "}");
    }

    /** Fresh DB view of a course (bypasses the first-level cache). */
    private Course fresh(Long id) {
        entityManager.flush();
        entityManager.clear();
        return courseRepository.findById(id).orElseThrow();
    }

    private List<Registration> regs(String studentId, Long courseId) {
        List<Registration> out = new ArrayList<>();
        for (Registration r : registrationRepository.findAll()) {
            if (r.getStudent().getStudentId().equals(studentId) && r.getCourse().getId().equals(courseId)) {
                out.add(r);
            }
        }
        return out;
    }

    private void assertNothingWritten(long regsBefore) {
        entityManager.flush();
        assertThat(registrationRepository.count()).as("registrations").isEqualTo(regsBefore);
        assertThat(auditLogRepository.count()).as("audit rows").isZero();
    }

    private static Set<String> keys(String json) throws Exception {
        JsonNode n = new ObjectMapper().readTree(json);
        Set<String> keys = new TreeSet<>();
        for (Iterator<String> it = n.fieldNames(); it.hasNext();) {
            keys.add(it.next());
        }
        return keys;
    }

    @Test
    @DisplayName("FR3-AC1: POST for CS101 returns 201 with exactly the five contract keys")
    void registerSuccess() throws Exception {
        String body = register(ALICE, cs101Id)
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Successfully registered for CS101."))
                .andExpect(jsonPath("$.courseId").value(cs101Id))
                .andExpect(jsonPath("$.courseCode").value("CS101"))
                .andExpect(jsonPath("$.availableSeats").value(49))
                .andExpect(jsonPath("$.registrationId").isNumber())
                .andReturn().getResponse().getContentAsString();
        assertThat(keys(body)).containsExactlyInAnyOrder(
                "message", "registrationId", "courseId", "courseCode", "availableSeats");
    }

    @Test
    @DisplayName("FR3-AC2: one Registration is stored with the fixed-clock date and the listing shows registered true")
    void registrationStored() throws Exception {
        register(ALICE, cs101Id).andExpect(status().isCreated());
        entityManager.flush();
        entityManager.clear();

        List<Registration> list = regs(ALICE_ID, cs101Id);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getRegistrationDate()).isEqualTo(FIXED);

        String body = mockMvc.perform(get("/api/courses").with(ALICE))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        boolean registered = false;
        for (JsonNode c : mapper.readTree(body).get("courses")) {
            if ("CS101".equals(c.get("code").asText())) {
                registered = c.get("registered").asBoolean();
            }
        }
        assertThat(registered).isTrue();
    }

    @Test
    @DisplayName("FR3-AC3: seats drop by 1 per registration, version increases, other courses are unchanged")
    void seatsDecrement() throws Exception {
        Long cs201 = idOf("CS201", "115-1");
        Long ma101 = idOf("MA101", "115-1");
        Long versionBefore = fresh(cs101Id).getVersion();

        register(ALICE, cs101Id).andExpect(status().isCreated());

        Course after = fresh(cs101Id);
        assertThat(after.getAvailableSeats()).isEqualTo(49);
        assertThat(after.getVersion()).isGreaterThan(versionBefore);
        assertThat(fresh(cs201).getAvailableSeats()).isEqualTo(40);
        assertThat(fresh(ma101).getAvailableSeats()).isZero();

        register(BOB, cs101Id).andExpect(status().isCreated())
                .andExpect(jsonPath("$.availableSeats").value(48));
    }

    @Test
    @DisplayName("FR3-AC4a: registering twice gives 409 'You are already registered for CS101.' and changes nothing more")
    void duplicate() throws Exception {
        register(ALICE, cs101Id).andExpect(status().isCreated());
        register(ALICE, cs101Id)
                .andExpect(jsonError(409, "You are already registered for CS101."));

        assertThat(fresh(cs101Id).getAvailableSeats()).isEqualTo(49);
        assertThat(regs(ALICE_ID, cs101Id)).hasSize(1);
        assertThat(auditLogRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("FR3-AC4b: Carol re-registering MA101 gets 'already registered' (checked before 'full'); seats stay 0")
    void duplicateBeforeFull() throws Exception {
        Long ma101 = idOf("MA101", "115-1");
        register(CAROL, ma101).andExpect(jsonError(409, "You are already registered for MA101."));
        assertThat(fresh(ma101).getAvailableSeats()).isZero();
    }

    @Test
    @DisplayName("FR3-AC5: exactly one REGISTER audit row with student, course and fixed-clock timestamp")
    void auditRow() throws Exception {
        register(ALICE, cs101Id).andExpect(status().isCreated());
        entityManager.flush();
        List<AuditLog> rows = auditLogRepository.findAll();
        assertThat(rows).hasSize(1);
        AuditLog row = rows.get(0);
        assertThat(row.getStudentId()).isEqualTo(ALICE_ID);
        assertThat(row.getCourseId()).isEqualTo(cs101Id);
        assertThat(row.getAction()).isEqualTo(AuditAction.REGISTER);
        assertThat(row.getTimestamp()).isEqualTo(FIXED);
    }

    @Test
    @DisplayName("FR3-AC6: the student comes from the security context, a studentId in the body is ignored")
    void identityFromSecurityContext() throws Exception {
        postJson(BOB, "{\"courseId\":" + cs101Id + ",\"studentId\":\"" + ALICE_ID + "\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.courseCode").value("CS101"));
        entityManager.flush();
        entityManager.clear();

        assertThat(regs("410000002", cs101Id)).hasSize(1);
        assertThat(regs(ALICE_ID, cs101Id)).isEmpty();
        List<AuditLog> rows = auditLogRepository.findAll();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getStudentId()).isEqualTo("410000002");
    }

    @Test
    @DisplayName("FR3-EX1: a course with 0 seats gives 409 'Course Full: MA101 has no available seats.' and writes nothing")
    void fullCourse() throws Exception {
        Long ma101 = idOf("MA101", "115-1");
        long regsBefore = registrationRepository.count();
        register(BOB, ma101).andExpect(jsonError(409, "Course Full: MA101 has no available seats."));
        assertThat(fresh(ma101).getAvailableSeats()).isZero();
        assertNothingWritten(regsBefore);
    }

    @Test
    @DisplayName("FR3-EX2: unknown or non-positive course id gives 404 'Course not found.' and writes nothing")
    void courseNotFound() throws Exception {
        long regsBefore = registrationRepository.count();
        postJson(ALICE, "{\"courseId\":999999}").andExpect(jsonError(404, "Course not found."));
        postJson(ALICE, "{\"courseId\":0}").andExpect(jsonError(404, "Course not found."));
        assertNothingWritten(regsBefore);
    }

    @Test
    @DisplayName("FR3-EX3: missing courseId gives 400 'Course ID is required.', bad JSON gives 400 'Malformed request.'")
    void badBodies() throws Exception {
        long regsBefore = registrationRepository.count();
        postJson(ALICE, "{}").andExpect(jsonError(400, "Course ID is required."));
        postJson(ALICE, "{\"courseId\":null}").andExpect(jsonError(400, "Course ID is required."));
        postJson(ALICE, "{\"courseId\":\"abc\"}").andExpect(jsonError(400, MSG_MALFORMED));
        postJson(ALICE, "{not json").andExpect(jsonError(400, MSG_MALFORMED));
        assertNothingWritten(regsBefore);
    }

    @Test
    @DisplayName("FR3-EX4: a missing CSRF token gives 403 'Invalid or missing CSRF token.' and writes nothing")
    void missingCsrf() throws Exception {
        long regsBefore = registrationRepository.count();
        mockMvc.perform(post("/api/registrations").with(ALICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":" + cs101Id + "}"))
                .andExpect(jsonError(403, MSG_CSRF_INVALID));
        assertNothingWritten(regsBefore);
        assertThat(fresh(cs101Id).getAvailableSeats()).isEqualTo(50);
    }

    @Test
    @DisplayName("FR3-EX5: a course of another semester (CS102 in 114-2) can be registered")
    void anySemester() throws Exception {
        register(ALICE, idOf("CS102", "114-2"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Successfully registered for CS102."))
                .andExpect(jsonPath("$.availableSeats").value(44));
    }

    @Test
    @DisplayName("FR3-EX6: the registration period is not enforced (clock outside both periods still gives 201)")
    void periodNotEnforced() throws Exception {
        clock.set("2027-06-01T09:00:00+08:00");
        register(ALICE, cs101Id).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("FR3-EX7: an authenticated principal with no student row gets 404 'Student not found.' and nothing changes")
    void unknownStudent() throws Exception {
        long regsBefore = registrationRepository.count();
        register(user("499999999").roles("STUDENT"), cs101Id)
                .andExpect(jsonError(404, MSG_STUDENT_NOT_FOUND));
        assertNothingWritten(regsBefore);
        assertThat(fresh(cs101Id).getAvailableSeats()).isEqualTo(50);
    }

    @Test
    @DisplayName("FR3-EX9: failed attempts write no audit rows, only successes do")
    void failuresWriteNoAudit() throws Exception {
        Long ma101 = idOf("MA101", "115-1");
        register(ALICE, cs101Id).andExpect(status().isCreated());
        register(BOB, ma101).andExpect(status().isConflict());
        register(ALICE, cs101Id).andExpect(status().isConflict());
        postJson(ALICE, "{\"courseId\":999999}").andExpect(status().isNotFound());
        register(user("499999999").roles("STUDENT"), cs101Id).andExpect(status().isNotFound());

        entityManager.flush();
        assertThat(auditLogRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("NFR4-AC1: every successful registration writes exactly one audit row")
    void oneAuditPerSuccess() throws Exception {
        register(ALICE, cs101Id).andExpect(status().isCreated());
        register(BOB, cs101Id).andExpect(status().isCreated());
        entityManager.flush();
        List<AuditLog> rows = auditLogRepository.findAll();
        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(AuditLog::getAction).containsOnly(AuditAction.REGISTER);
        assertThat(rows).extracting(AuditLog::getStudentId).containsExactlyInAnyOrder(ALICE_ID, "410000002");
    }
}
