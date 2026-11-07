package edu.tku.registration.service;

import static edu.tku.registration.service.CapacityTestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import edu.tku.registration.domain.Course;
import edu.tku.registration.repository.CourseRepository;
import edu.tku.registration.repository.SemesterRepository;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** FR6-EX1 / FR6-EX2: deterministic retry outcomes. Not @Transactional. */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(edu.tku.registration.support.PinnedClockConfig.class)
class CourseFullRetryTest {

    private static final String ALICE = "410000001";
    private static final String BOB = "410000002";

    @Autowired private RegistrationService registrationService;
    @Autowired private SemesterRepository semesterRepository;
    @Autowired private JdbcTemplate jdbc;
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;
    @MockitoSpyBean private CourseRepository courseRepository;

    private Long courseId;

    @AfterEach
    void cleanUp() {
        if (courseId != null) {
            removeCourse(jdbc, courseId);
        }
    }

    private void create(String code, int capacity, int seats) {
        courseId = createCourse(courseRepository, semesterRepository, code, capacity, seats);
        clearInvocations(courseRepository);
    }

    private org.springframework.data.jpa.repository.support.SimpleJpaRepository<Course, Long> realRepo() {
        return new org.springframework.data.jpa.repository.support.SimpleJpaRepository<>(Course.class, entityManager);
    }

    /** JdbcTemplate would join the caller's transaction, so the update runs in its own thread to be committed. */
    private void committedSeatUpdate(Long id, int seats) throws Exception {
        ExecutorService other = Executors.newSingleThreadExecutor();
        try {
            other.submit(() -> jdbc.update("update course set available_seats = ? where id = ?", seats, id))
                    .get(30, TimeUnit.SECONDS);
        } finally {
            other.shutdownNow();
        }
    }

    @Test
    @DisplayName("FR6-EX1: a loser whose first attempt hits an optimistic-lock conflict ends with CourseFullException, not the conflict message")
    void loserEndsWithCourseFull() {
        create("EX1CRS", 1, 1);
        AtomicBoolean first = new AtomicBoolean(false);
        Long id = courseId;
        doAnswer(inv -> {
            if (first.compareAndSet(false, true)) {
                // another student takes the last seat (committed in its own thread/transaction)
                ExecutorService other = Executors.newSingleThreadExecutor();
                try {
                    other.submit(() -> registrationService.register(ALICE, id)).get(30, TimeUnit.SECONDS);
                } finally {
                    other.shutdownNow();
                }
                throw new ObjectOptimisticLockingFailureException(Course.class, id);
            }
            return realRepo().saveAndFlush(inv.<Course>getArgument(0));
        }).when(courseRepository).saveAndFlush(any(Course.class));

        assertThatThrownBy(() -> registrationService.register(BOB, id))
                .isInstanceOf(CourseFullException.class)
                .hasMessage("Course Full: EX1CRS has no available seats.");

        assertThat(regs(jdbc, id)).isEqualTo(1);
        assertThat(audits(jdbc, id)).isEqualTo(1);
        assertThat(seats(jdbc, id)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from registration r join student s on s.id = r.student_id "
                + "where r.course_id = ? and s.student_id = ?", Integer.class, id, ALICE)).isEqualTo(1);
    }

    @Test
    @DisplayName("FR6-EX2: after 5 conflicting attempts, if the course is full at that moment, CourseFullException is thrown (not the conflict)")
    void finalFullCheckAfterExhaustedRetries() {
        create("EX2CRS", 5, 1);
        Long id = courseId;
        AtomicInteger calls = new AtomicInteger();
        // seats stay at 1 so the guard passes on attempts 1-5; only the 5th conflict leaves 0 seats
        doAnswer(inv -> {
            int n = calls.incrementAndGet();
            committedSeatUpdate(id, n < 5 ? 1 : 0);
            throw new ObjectOptimisticLockingFailureException(Course.class, id);
        }).when(courseRepository).saveAndFlush(any(Course.class));

        assertThatThrownBy(() -> registrationService.register(ALICE, id))
                .isInstanceOf(CourseFullException.class)
                .hasMessage("Course Full: EX2CRS has no available seats.");

        verify(courseRepository, times(5)).saveAndFlush(any(Course.class));
        assertThat(regs(jdbc, id)).isZero();
        assertThat(audits(jdbc, id)).isZero();
    }

    @Test
    @DisplayName("FR6-EX2: the literal spec case (stub sets seats to 0 on every call) also ends with CourseFullException")
    void finalFullCheckSeatsZeroOnEveryCall() {
        create("EX2LIT", 5, 5);
        Long id = courseId;
        doAnswer(inv -> {
            committedSeatUpdate(id, 0);
            throw new ObjectOptimisticLockingFailureException(Course.class, id);
        }).when(courseRepository).saveAndFlush(any(Course.class));

        assertThatThrownBy(() -> registrationService.register(ALICE, id))
                .isInstanceOf(CourseFullException.class)
                .hasMessage("Course Full: EX2LIT has no available seats.");
        assertThat(regs(jdbc, id)).isZero();
        assertThat(audits(jdbc, id)).isZero();
    }

    @Test
    @DisplayName("FR6-EX2: with seats still left after 5 conflicting attempts the conflict message is kept (FR3-EX11)")
    void conflictKeptWhenSeatsLeft() {
        create("EX2CNF", 5, 5);
        Long id = courseId;
        doAnswer(inv -> {
            throw new ObjectOptimisticLockingFailureException(Course.class, id);
        }).when(courseRepository).saveAndFlush(any(Course.class));

        assertThatThrownBy(() -> registrationService.register(ALICE, id))
                .isInstanceOf(RegistrationConflictException.class)
                .hasMessage("The course was just updated by another student. Please try again.");
        verify(courseRepository, times(5)).saveAndFlush(any(Course.class));
    }
}
