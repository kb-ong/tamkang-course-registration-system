package edu.tku.registration.service;

import static edu.tku.registration.web.ApiTestSupport.ALICE_ID;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import edu.tku.registration.domain.AuditLog;
import edu.tku.registration.domain.Course;
import edu.tku.registration.repository.AuditLogRepository;
import edu.tku.registration.repository.CourseRepository;
import edu.tku.registration.repository.RegistrationRepository;
import edu.tku.registration.repository.SemesterRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Atomicity and retry tests (FR3-EX8, EX10, EX11). Not @Transactional. Each test uses its own
 * course (50 seats, like CS101) so no seed course is mutated; the context is dirtied afterwards.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(edu.tku.registration.support.PinnedClockConfig.class)
class RegistrationRetryAndAtomicityTest {

    @Autowired private RegistrationService registrationService;
    @Autowired private RegistrationRepository registrationRepository;
    @Autowired private SemesterRepository semesterRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mockMvc;
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;
    @MockitoSpyBean private CourseRepository courseRepository;
    @MockitoSpyBean private AuditLogRepository auditLogRepository;

    private Long courseId;

    @BeforeEach
    void createCourse() {
        Course c = new Course("RETRY1", "Retry Test", 3, "Prof. Test", 50, 50,
                semesterRepository.findByCode("115-1").orElseThrow());
        courseId = courseRepository.saveAndFlush(c).getId();
        clearInvocations(courseRepository, auditLogRepository);
    }

    @AfterEach
    void cleanUp() {
        // spies are reset by the framework after each test; remove rows with plain SQL to be safe
        jdbc.update("delete from audit_log where course_id = ?", courseId);
        jdbc.update("delete from registration where course_id = ?", courseId);
        jdbc.update("delete from course where id = ?", courseId);
    }

    /** Spring Data proxy methods are abstract for Mockito, so delegate "real" calls to an equivalent JPA repository. */
    private org.springframework.data.jpa.repository.support.SimpleJpaRepository<Course, Long> realCourseRepository() {
        return new org.springframework.data.jpa.repository.support.SimpleJpaRepository<>(Course.class, entityManager);
    }

    private int seats() {
        return jdbc.queryForObject("select available_seats from course where id = ?", Integer.class, courseId);
    }

    private int regCount() {
        return jdbc.queryForObject("select count(*) from registration where course_id = ?", Integer.class, courseId);
    }

    private int auditCount() {
        return jdbc.queryForObject("select count(*) from audit_log where course_id = ?", Integer.class, courseId);
    }

    @Test
    @DisplayName("FR3-EX8: if the audit write fails the exception propagates and seats and registration are rolled back")
    void auditFailureRollsBackEverything() throws Exception {
        doThrow(new RuntimeException("audit boom")).when(auditLogRepository).save(any(AuditLog.class));

        assertThatThrownBy(() -> registrationService.register(ALICE_ID, courseId))
                .isInstanceOf(RuntimeException.class);
        assertThat(seats()).isEqualTo(50);
        assertThat(regCount()).isZero();
        assertThat(auditCount()).isZero();

        mockMvc.perform(post("/api/registrations").with(user("410000002").roles("STUDENT")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"courseId\":" + courseId + "}"))
                .andExpect(jsonError(500, "An unexpected error occurred."));
        assertThat(seats()).isEqualTo(50);
        assertThat(regCount()).isZero();
    }

    @Test
    @DisplayName("NFR4-AC1: the audit row is part of the registration transaction (audit failure leaves no registration)")
    void auditIsTransactional() {
        doThrow(new RuntimeException("audit boom")).when(auditLogRepository).save(any(AuditLog.class));
        assertThatThrownBy(() -> registrationService.register(ALICE_ID, courseId))
                .isInstanceOf(RuntimeException.class);
        assertThat(regCount()).isZero();
        assertThat(seats()).isEqualTo(50);
    }

    @Test
    @DisplayName("FR3-EX10: one optimistic-lock failure is retried; seats drop exactly once, one registration, one audit row")
    void retryAfterOneConflict() {
        doThrow(new ObjectOptimisticLockingFailureException(Course.class, courseId))
                .doAnswer(inv -> realCourseRepository().saveAndFlush(inv.<Course>getArgument(0)))
                .when(courseRepository).saveAndFlush(any(Course.class));

        RegistrationResult result = registrationService.register(ALICE_ID, courseId);

        assertThat(result.courseId()).isEqualTo(courseId);
        assertThat(seats()).isEqualTo(49);
        assertThat(regCount()).isEqualTo(1);
        assertThat(auditCount()).isEqualTo(1);
        verify(courseRepository, times(2)).saveAndFlush(any(Course.class));
    }

    @Test
    @DisplayName("FR3-EX11: five failed attempts throw RegistrationConflictException and leave no trace")
    void retriesExhausted() {
        doThrow(new ObjectOptimisticLockingFailureException(Course.class, courseId))
                .when(courseRepository).saveAndFlush(any(Course.class));

        assertThatThrownBy(() -> registrationService.register(ALICE_ID, courseId))
                .isInstanceOf(RegistrationConflictException.class)
                .hasMessage("The course was just updated by another student. Please try again.");

        verify(courseRepository, times(5)).saveAndFlush(any(Course.class));
        assertThat(seats()).isEqualTo(50);
        assertThat(regCount()).isZero();
        assertThat(auditCount()).isZero();
    }

    @Test
    @DisplayName("FR3-EX11: via HTTP, exhausted retries return 409 with the conflict message")
    void retriesExhaustedOverHttp() throws Exception {
        doThrow(new ObjectOptimisticLockingFailureException(Course.class, courseId))
                .when(courseRepository).saveAndFlush(any(Course.class));

        mockMvc.perform(post("/api/registrations").with(user(ALICE_ID).roles("STUDENT")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"courseId\":" + courseId + "}"))
                .andExpect(jsonError(409, "The course was just updated by another student. Please try again."));
        assertThat(seats()).isEqualTo(50);
        assertThat(regCount()).isZero();
        assertThat(auditCount()).isZero();
    }
}
