package edu.tku.registration.web;

import static edu.tku.registration.support.MutableTestClock.*;
import static edu.tku.registration.web.ApiTestSupport.ALICE_ID;
import static edu.tku.registration.web.ApiTestSupport.MSG_AUTH_REQUIRED;
import static edu.tku.registration.web.ApiTestSupport.MSG_CSRF_INVALID;
import static edu.tku.registration.web.ApiTestSupport.MSG_MALFORMED;
import static edu.tku.registration.web.ApiTestSupport.MSG_STUDENT_NOT_FOUND;
import static edu.tku.registration.web.ApiTestSupport.jsonError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import edu.tku.registration.domain.Semester;
import edu.tku.registration.repository.AuditLogRepository;
import edu.tku.registration.repository.CourseRepository;
import edu.tku.registration.repository.RegistrationPeriodRepository;
import edu.tku.registration.repository.RegistrationRepository;
import edu.tku.registration.repository.SemesterRepository;
import edu.tku.registration.repository.StudentRepository;
import edu.tku.registration.support.MutableTestClock;
import edu.tku.registration.support.PeriodTestData;
import edu.tku.registration.support.PinnedClockConfig;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
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

/**
 * DELETE /api/registrations/{courseId} (spec FR4). Rolled back after each test, Clock pinned at T_OPEN.
 * csrf() is fine here; the real cookie flow is in DropCsrfCookieFlowTest (own context).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(PinnedClockConfig.class)
class DropControllerTest {

    private static final RequestPostProcessor ALICE = user(ALICE_ID).roles("STUDENT");
    private static final RequestPostProcessor BOB = user("410000002").roles("STUDENT");
    private static final RequestPostProcessor CAROL = user("410000003").roles("STUDENT");
    private static final LocalDateTime FIXED = LocalDateTime.of(2026, 9, 1, 9, 0, 0);

    @Autowired private MockMvc mockMvc;
    @Autowired private MutableTestClock clock;
    @Autowired private CourseRepository courseRepository;
    @Autowired private SemesterRepository semesterRepository;
    @Autowired private RegistrationPeriodRepository periodRepository;
    @Autowired private RegistrationRepository registrationRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private EntityManager entityManager;
    private final ObjectMapper mapper = new ObjectMapper();

    private Long cs101Id;
    private Long ma101Id;

    @BeforeEach
    void setUp() {
        clock.set(T_OPEN);
        cs101Id = idOf("CS101", "115-1");
        ma101Id = idOf("MA101", "115-1");
    }

    private Long idOf(String code, String semester) {
        return courseRepository.findByCourseCodeAndSemester_Code(code, semester).orElseThrow().getId();
    }

    private ResultActions register(RequestPostProcessor who, Long courseId) throws Exception {
        return mockMvc.perform(post("/api/registrations").with(who).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"courseId\":" + courseId + "}"));
    }

    private ResultActions drop(RequestPostProcessor who, Object courseId) throws Exception {
        return mockMvc.perform(delete("/api/registrations/" + courseId).with(who).with(csrf()));
    }

    private Course fresh(Long id) {
        entityManager.flush();
        entityManager.clear();
        return courseRepository.findById(id).orElseThrow();
    }

    private long regCount() {
        entityManager.flush();
        return registrationRepository.count();
    }

    private long auditCount() {
        entityManager.flush();
        return auditLogRepository.count();
    }

    private List<Registration> regs(String studentId, Long courseId) {
        entityManager.flush();
        List<Registration> out = new ArrayList<>();
        for (Registration r : registrationRepository.findAll()) {
            if (r.getStudent().getStudentId().equals(studentId) && r.getCourse().getId().equals(courseId)) {
                out.add(r);
            }
        }
        return out;
    }

    private List<AuditLog> audits(String studentId, Long courseId) {
        entityManager.flush();
        List<AuditLog> out = new ArrayList<>();
        for (AuditLog a : auditLogRepository.findAll()) {
            if (a.getStudentId().equals(studentId) && a.getCourseId().equals(courseId)) {
                out.add(a);
            }
        }
        out.sort((x, y) -> Long.compare(x.getId(), y.getId()));
        return out;
    }

    /** availableSeats + registrations - capacity (must be 0). */
    private long invariantGap(Long courseId) {
        Course c = fresh(courseId);
        long n = registrationRepository.findAll().stream()
                .filter(r -> r.getCourse().getId().equals(courseId)).count();
        return c.getAvailableSeats() + n - c.getCapacity();
    }

    private static Set<String> keys(JsonNode n) {
        Set<String> keys = new TreeSet<>();
        for (Iterator<String> it = n.fieldNames(); it.hasNext();) {
            keys.add(it.next());
        }
        return keys;
    }

    private JsonNode body(ResultActions r) throws Exception {
        return mapper.readTree(r.andReturn().getResponse().getContentAsString());
    }

    @Test
    @DisplayName("FR4-AC1: after Alice registers CS101, GET /api/registrations contains CS101 so it can be dropped")
    void listContainsRegistered() throws Exception {
        register(ALICE, cs101Id).andExpect(status().isCreated());
        mockMvc.perform(get("/api/registrations").with(ALICE)).andExpect(status().isOk())
                .andExpect(jsonPath("$.courses.length()").value(1))
                .andExpect(jsonPath("$.courses[0].code").value("CS101"));
    }

    @Test
    @DisplayName("FR4-AC2: DELETE returns 200 with exactly message, courseId, courseCode, availableSeats and removes the registration")
    void dropSuccess() throws Exception {
        register(ALICE, cs101Id).andExpect(status().isCreated());
        ResultActions r = drop(ALICE, cs101Id).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Successfully dropped CS101."))
                .andExpect(jsonPath("$.courseId").value(cs101Id))
                .andExpect(jsonPath("$.courseCode").value("CS101"))
                .andExpect(jsonPath("$.availableSeats").value(50));
        assertThat(keys(body(r))).containsExactlyInAnyOrder("message", "courseId", "courseCode", "availableSeats");
        assertThat(regs(ALICE_ID, cs101Id)).isEmpty();

        drop(ALICE, cs101Id).andExpect(jsonError(404, "You are not registered for CS101."));
    }

    @Test
    @DisplayName("FR4-AC3: a drop gives the seat back (+1), bumps the version, leaves other courses alone, keeps the invariant")
    void seatPlusOne() throws Exception {
        Long cs201 = idOf("CS201", "115-1");
        register(ALICE, cs101Id).andExpect(status().isCreated());
        Course afterRegister = fresh(cs101Id);
        assertThat(afterRegister.getAvailableSeats()).isEqualTo(49);
        Long versionBefore = afterRegister.getVersion();

        drop(ALICE, cs101Id).andExpect(status().isOk());

        Course after = fresh(cs101Id);
        assertThat(after.getAvailableSeats()).isEqualTo(50);
        assertThat(after.getVersion()).isGreaterThan(versionBefore);
        assertThat(fresh(cs201).getAvailableSeats()).isEqualTo(40);
        assertThat(fresh(ma101Id).getAvailableSeats()).isZero();
        assertThat(invariantGap(cs101Id)).isZero();

        drop(CAROL, ma101Id).andExpect(status().isOk()).andExpect(jsonPath("$.availableSeats").value(1));
        assertThat(fresh(ma101Id).getAvailableSeats()).isEqualTo(1);
        assertThat(invariantGap(ma101Id)).isZero();
        assertThat(invariantGap(cs101Id)).isZero();
    }

    @Test
    @DisplayName("FR4-AC4: after the drop the schedule is empty with totalCredits 0 and the listing shows registered false")
    void removedFromSchedule() throws Exception {
        register(ALICE, cs101Id).andExpect(status().isCreated());
        drop(ALICE, cs101Id).andExpect(status().isOk());

        mockMvc.perform(get("/api/registrations").with(ALICE)).andExpect(status().isOk())
                .andExpect(jsonPath("$.courses.length()").value(0))
                .andExpect(jsonPath("$.totalCredits").value(0));

        JsonNode l = body(mockMvc.perform(get("/api/courses").with(ALICE)).andExpect(status().isOk()));
        boolean found = false;
        for (JsonNode c : l.get("courses")) {
            if ("CS101".equals(c.get("code").asText())) {
                found = true;
                assertThat(c.get("registered").asBoolean()).isFalse();
            }
        }
        assertThat(found).isTrue();
    }

    @Test
    @DisplayName("FR4-AC5: the confirmation message is exactly 'Successfully dropped {courseCode}.'")
    void confirmationMessage() throws Exception {
        drop(CAROL, ma101Id).andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Successfully dropped MA101."));
    }

    @Test
    @DisplayName("FR4-AC6: one DROP audit row with student, course and fixed-clock timestamp; the REGISTER row stays")
    void auditRow() throws Exception {
        register(ALICE, cs101Id).andExpect(status().isCreated());
        long before = auditCount();
        drop(ALICE, cs101Id).andExpect(status().isOk());

        assertThat(auditCount()).isEqualTo(before + 1);
        List<AuditLog> rows = audits(ALICE_ID, cs101Id);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getAction()).isEqualTo(AuditAction.REGISTER);
        AuditLog d = rows.get(1);
        assertThat(d.getAction()).isEqualTo(AuditAction.DROP);
        assertThat(d.getStudentId()).isEqualTo(ALICE_ID);
        assertThat(d.getCourseId()).isEqualTo(cs101Id);
        assertThat(d.getTimestamp()).isEqualTo(FIXED);
    }

    @Test
    @DisplayName("FR4-EX1: Bob (not registered) gets 404 'You are not registered for CS101.' and nothing changes")
    void notRegistered() throws Exception {
        Long version = fresh(cs101Id).getVersion();
        long regsBefore = regCount();
        long auditsBefore = auditCount();

        drop(BOB, cs101Id).andExpect(jsonError(404, "You are not registered for CS101."));

        Course c = fresh(cs101Id);
        assertThat(c.getAvailableSeats()).isEqualTo(50);
        assertThat(c.getVersion()).isEqualTo(version);
        assertThat(regCount()).isEqualTo(regsBefore);
        assertThat(auditCount()).isEqualTo(auditsBefore);
    }

    @Test
    @DisplayName("FR4-EX2: unknown, zero or negative id gives 404 'Course not found.'; a non-numeric id gives 400 'Malformed request.'")
    void courseNotFoundOrMalformed() throws Exception {
        long regsBefore = regCount();
        long auditsBefore = auditCount();
        drop(ALICE, "999999").andExpect(jsonError(404, "Course not found."));
        drop(ALICE, "0").andExpect(jsonError(404, "Course not found."));
        drop(ALICE, "-5").andExpect(jsonError(404, "Course not found."));
        drop(ALICE, "abc").andExpect(jsonError(400, MSG_MALFORMED));
        assertThat(regCount()).isEqualTo(regsBefore);
        assertThat(auditCount()).isEqualTo(auditsBefore);
    }

    @ParameterizedTest(name = "FR4-EX3: at {0} Carol dropping MA101 gets 409 and nothing changes")
    @ValueSource(strings = {T_LATE, T_BEFORE_START, T_AFTER_END})
    @DisplayName("FR4-EX3: outside the period the drop is refused with 409 'Dropping is closed for semester 115-1.'")
    void closedOutsidePeriod(String instant) throws Exception {
        Long version = fresh(ma101Id).getVersion();
        long regsBefore = regCount();
        long auditsBefore = auditCount();
        clock.set(instant);

        drop(CAROL, ma101Id).andExpect(jsonError(409, "Dropping is closed for semester 115-1."));

        Course c = fresh(ma101Id);
        assertThat(c.getAvailableSeats()).isZero();
        assertThat(c.getVersion()).isEqualTo(version);
        assertThat(regs("410000003", ma101Id)).hasSize(1);
        assertThat(regCount()).isEqualTo(regsBefore);
        assertThat(auditCount()).isEqualTo(auditsBefore);
    }

    @Test
    @DisplayName("FR4-EX3: a course in a semester without a period cannot be dropped: 409 'Dropping is closed for semester NOPD-1.'")
    void closedWithoutPeriod() throws Exception {
        Semester s = PeriodTestData.semester(semesterRepository, periodRepository, "NOPD-1", null, null);
        Course np = PeriodTestData.course(courseRepository, s, "NP101", 5, 4);
        registrationRepository.saveAndFlush(new Registration(
                studentRepository.findByStudentId(ALICE_ID).orElseThrow(), np, FIXED));
        long regsBefore = regCount();
        long auditsBefore = auditCount();

        drop(ALICE, np.getId()).andExpect(jsonError(409, "Dropping is closed for semester NOPD-1."));

        assertThat(regCount()).isEqualTo(regsBefore);
        assertThat(auditCount()).isEqualTo(auditsBefore);
        assertThat(fresh(np.getId()).getAvailableSeats()).isEqualTo(4);
    }

    @ParameterizedTest(name = "FR4-EX3: at {0} the drop succeeds (inclusive bounds)")
    @ValueSource(strings = {T_START, T_END, T_END_MS})
    @DisplayName("FR4-EX3: at the inclusive period bounds the drop succeeds")
    void inclusiveBounds(String instant) throws Exception {
        clock.set(instant);
        drop(CAROL, ma101Id).andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Successfully dropped MA101."));
    }

    @Test
    @DisplayName("FR4-EX4: Alice cannot drop Carol's registration: 404 'You are not registered for MA101.' and nothing changes")
    void ownership() throws Exception {
        long auditsBefore = auditCount();
        drop(ALICE, ma101Id).andExpect(jsonError(404, "You are not registered for MA101."));
        assertThat(regs("410000003", ma101Id)).hasSize(1);
        assertThat(fresh(ma101Id).getAvailableSeats()).isZero();
        assertThat(auditCount()).isEqualTo(auditsBefore);
    }

    @Test
    @DisplayName("FR4-EX5: anonymous DELETE gives 401, an authenticated DELETE without a CSRF token gives 403, nothing is written")
    void securityAndCsrf() throws Exception {
        long regsBefore = regCount();
        long auditsBefore = auditCount();

        mockMvc.perform(delete("/api/registrations/" + ma101Id).with(csrf()))
                .andExpect(jsonError(401, MSG_AUTH_REQUIRED));
        mockMvc.perform(delete("/api/registrations/" + ma101Id).with(CAROL))
                .andExpect(jsonError(403, MSG_CSRF_INVALID));

        assertThat(regCount()).isEqualTo(regsBefore);
        assertThat(auditCount()).isEqualTo(auditsBefore);
        assertThat(fresh(ma101Id).getAvailableSeats()).isZero();
    }

    @Test
    @DisplayName("FR4-EX12: register, drop and register again: seats 50, 49, 50, 49 and audit REGISTER, DROP, REGISTER in order")
    void reRegisterAfterDrop() throws Exception {
        register(ALICE, cs101Id).andExpect(status().isCreated()).andExpect(jsonPath("$.availableSeats").value(49));
        drop(ALICE, cs101Id).andExpect(status().isOk()).andExpect(jsonPath("$.availableSeats").value(50));
        register(ALICE, cs101Id).andExpect(status().isCreated()).andExpect(jsonPath("$.availableSeats").value(49));

        assertThat(fresh(cs101Id).getAvailableSeats()).isEqualTo(49);
        assertThat(audits(ALICE_ID, cs101Id)).extracting(AuditLog::getAction)
                .containsExactly(AuditAction.REGISTER, AuditAction.DROP, AuditAction.REGISTER);
    }

    @Test
    @DisplayName("FR4-EX13: a rejected drop (not registered, closed, not found, not owner) never writes an audit row")
    void rejectedNotAudited() throws Exception {
        long before = auditCount();
        drop(BOB, cs101Id).andExpect(status().isNotFound());
        drop(ALICE, "999999").andExpect(status().isNotFound());
        drop(ALICE, ma101Id).andExpect(status().isNotFound());
        clock.set(T_LATE);
        drop(CAROL, ma101Id).andExpect(status().isConflict());
        drop(BOB, cs101Id).andExpect(status().isConflict());
        assertThat(auditCount()).isEqualTo(before);
    }

    @Test
    @DisplayName("NFR4-AC2: a successful drop writes exactly one DROP row, failed drops write none")
    void nfr4DropAudit() throws Exception {
        long before = auditCount();
        drop(BOB, cs101Id).andExpect(status().isNotFound());
        assertThat(auditCount()).isEqualTo(before);

        drop(CAROL, ma101Id).andExpect(status().isOk());
        assertThat(auditCount()).isEqualTo(before + 1);
        List<AuditLog> rows = audits("410000003", ma101Id);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getAction()).isEqualTo(AuditAction.DROP);
    }

    @Test
    @DisplayName("FR4-EX14: check order student, course, period, registration")
    void checkOrder() throws Exception {
        clock.set(T_LATE);
        drop(BOB, cs101Id).andExpect(jsonError(409, "Dropping is closed for semester 115-1."));
        drop(user("499999999").roles("STUDENT"), cs101Id).andExpect(jsonError(404, MSG_STUDENT_NOT_FOUND));
        clock.set(T_OPEN);
        drop(BOB, cs101Id).andExpect(jsonError(404, "You are not registered for CS101."));
        drop(user("499999999").roles("STUDENT"), cs101Id).andExpect(jsonError(404, MSG_STUDENT_NOT_FOUND));
    }

    @Test
    @DisplayName("FR4-EX15: the semester of the course decides: CS102 droppable at T_SPRING, closed at T_OPEN")
    void semesterOfCourseDecides() throws Exception {
        Long cs102 = idOf("CS102", "114-2");
        clock.set(T_SPRING);
        register(ALICE, cs102).andExpect(status().isCreated());
        drop(ALICE, cs102).andExpect(status().isOk()).andExpect(jsonPath("$.availableSeats").value(45));
        assertThat(fresh(cs102).getAvailableSeats()).isEqualTo(45);

        register(ALICE, cs102).andExpect(status().isCreated());
        clock.set(T_OPEN);
        drop(ALICE, cs102).andExpect(jsonError(409, "Dropping is closed for semester 114-2."));
    }
}
