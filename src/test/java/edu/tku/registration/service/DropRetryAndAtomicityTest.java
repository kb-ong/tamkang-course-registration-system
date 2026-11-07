package edu.tku.registration.service;

import static edu.tku.registration.service.CapacityTestData.*;
import static edu.tku.registration.web.ApiTestSupport.jsonError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

import edu.tku.registration.domain.AuditLog;
import edu.tku.registration.domain.Course;
import edu.tku.registration.repository.AuditLogRepository;
import edu.tku.registration.repository.CourseRepository;
import edu.tku.registration.repository.SemesterRepository;
import edu.tku.registration.support.MutableTestClock;
import edu.tku.registration.support.PinnedClockConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Atomicity and retry for drop (FR4-EX6, EX7, EX8, NFR4-AC2). Not @Transactional. Each test creates its own
 * course (capacity 50) and registers Alice first; the spies are stubbed only after that.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(PinnedClockConfig.class)
class DropRetryAndAtomicityTest {

    private static final String ALICE = "410000001";

    @Autowired private RegistrationService registrationService;
    @Autowired private SemesterRepository semesterRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mockMvc;
    @Autowired private MutableTestClock clock;
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;
    @MockitoSpyBean private CourseRepository courseRepository;
    @MockitoSpyBean private AuditLogRepository auditLogRepository;

    private Long courseId;

    @BeforeEach
    void setUp() {
        clock.set(MutableTestClock.T_OPEN);
        courseId = createCourse(courseRepository, semesterRepository, "DRETRY", 50, 50);
        registrationService.register(ALICE, courseId);
        clearInvocations(courseRepository, auditLogRepository);
    }

    @AfterEach
    void cleanUp() {
        removeCourse(jdbc, courseId);
    }

    private org.springframework.data.jpa.repository.support.SimpleJpaRepository<Course, Long> realRepo() {
        return new org.springframework.data.jpa.repository.support.SimpleJpaRepository<>(Course.class, entityManager);
    }

    private int dropAudits() {
        return jdbc.queryForObject(
                "select count(*) from audit_log where course_id = ? and action = 'DROP'", Integer.class, courseId);
    }

    @Test
    @DisplayName("FR4-EX6: if the audit write fails the drop propagates the exception and registration and seats are unchanged")
    void auditFailureRollsBack() throws Exception {
        doThrow(new RuntimeException("audit boom")).when(auditLogRepository).save(any(AuditLog.class));

        assertThatThrownBy(() -> registrationService.drop(ALICE, courseId)).isInstanceOf(RuntimeException.class);
        assertThat(regs(jdbc, courseId)).isEqualTo(1);
        assertThat(seats(jdbc, courseId)).isEqualTo(49);
        assertThat(dropAudits()).isZero();

        mockMvc.perform(delete("/api/registrations/" + courseId).with(user(ALICE).roles("STUDENT")).with(csrf()))
                .andExpect(jsonError(500, "An unexpected error occurred."));
        assertThat(regs(jdbc, courseId)).isEqualTo(1);
        assertThat(seats(jdbc, courseId)).isEqualTo(49);
    }

    @Test
    @DisplayName("NFR4-AC2: the DROP audit row belongs to the drop transaction (audit failure leaves no half-done drop)")
    void auditIsTransactional() {
        doThrow(new RuntimeException("audit boom")).when(auditLogRepository).save(any(AuditLog.class));
        assertThatThrownBy(() -> registrationService.drop(ALICE, courseId)).isInstanceOf(RuntimeException.class);
        assertThat(regs(jdbc, courseId)).isEqualTo(1);
        assertThat(seats(jdbc, courseId)).isEqualTo(49);
        assertThat(invariantGap(jdbc, courseId)).isZero();
    }

    @Test
    @DisplayName("FR4-EX7: one optimistic-lock failure is retried: seats rise by exactly 1, registration gone, one DROP row, 2 saveAndFlush calls")
    void retryAfterOneConflict() {
        doThrow(new ObjectOptimisticLockingFailureException(Course.class, courseId))
                .doAnswer(inv -> realRepo().saveAndFlush(inv.<Course>getArgument(0)))
                .when(courseRepository).saveAndFlush(any(Course.class));

        DropResult result = registrationService.drop(ALICE, courseId);

        assertThat(result.courseId()).isEqualTo(courseId);
        assertThat(seats(jdbc, courseId)).isEqualTo(50);
        assertThat(regs(jdbc, courseId)).isZero();
        assertThat(dropAudits()).isEqualTo(1);
        verify(courseRepository, times(2)).saveAndFlush(any(Course.class));
    }

    @Test
    @DisplayName("FR4-EX8: five failed attempts throw RegistrationConflictException and leave registration, seats and audit untouched")
    void retriesExhausted() {
        doThrow(new ObjectOptimisticLockingFailureException(Course.class, courseId))
                .when(courseRepository).saveAndFlush(any(Course.class));

        assertThatThrownBy(() -> registrationService.drop(ALICE, courseId))
                .isInstanceOf(RegistrationConflictException.class)
                .hasMessage("The course was just updated by another student. Please try again.");

        verify(courseRepository, times(5)).saveAndFlush(any(Course.class));
        assertThat(regs(jdbc, courseId)).isEqualTo(1);
        assertThat(seats(jdbc, courseId)).isEqualTo(49);
        assertThat(dropAudits()).isZero();
    }

    @Test
    @DisplayName("FR4-EX8: via HTTP, exhausted retries return 409 with the conflict message")
    void retriesExhaustedOverHttp() throws Exception {
        doThrow(new ObjectOptimisticLockingFailureException(Course.class, courseId))
                .when(courseRepository).saveAndFlush(any(Course.class));

        mockMvc.perform(delete("/api/registrations/" + courseId).with(user(ALICE).roles("STUDENT")).with(csrf()))
                .andExpect(jsonError(409, "The course was just updated by another student. Please try again."));
        assertThat(regs(jdbc, courseId)).isEqualTo(1);
        assertThat(seats(jdbc, courseId)).isEqualTo(49);
    }
}
