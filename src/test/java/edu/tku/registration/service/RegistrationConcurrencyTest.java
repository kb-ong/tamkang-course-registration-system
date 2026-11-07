package edu.tku.registration.service;

import static org.assertj.core.api.Assertions.assertThat;

import edu.tku.registration.domain.Course;
import edu.tku.registration.repository.CourseRepository;
import edu.tku.registration.repository.SemesterRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

/** Concurrency tests (FR3-EX12/13/14). Not @Transactional: every thread runs its own transactions. */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(edu.tku.registration.support.PinnedClockConfig.class)
class RegistrationConcurrencyTest {

    private static final String ALICE = "410000001";
    private static final String BOB = "410000002";
    private static final String CAROL = "410000003";

    @Autowired private RegistrationService registrationService;
    @Autowired private CourseRepository courseRepository;
    @Autowired private SemesterRepository semesterRepository;
    @Autowired private JdbcTemplate jdbc;

    private Long createCourse(String code, int seats) {
        Course c = new Course(code, "Concurrency Test " + code, 3, "Prof. Test", seats, seats,
                semesterRepository.findByCode("115-1").orElseThrow());
        return courseRepository.saveAndFlush(c).getId();
    }

    private void removeCourse(Long id) {
        jdbc.update("delete from audit_log where course_id = ?", id);
        jdbc.update("delete from registration where course_id = ?", id);
        jdbc.update("delete from course where id = ?", id);
    }

    private int count(String sql, Long id) {
        return jdbc.queryForObject(sql, Integer.class, id);
    }

    private int seats(Long id) {
        return count("select available_seats from course where id = ?", id);
    }

    private int regs(Long id) {
        return count("select count(*) from registration where course_id = ?", id);
    }

    private int audits(Long id) {
        return count("select count(*) from audit_log where course_id = ? and action = 'REGISTER'", id);
    }

    /** Runs register(student, courseId) for each student at the same moment. Returns the thrown exception or null. */
    private List<Throwable> race(Long courseId, String... students) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(students.length);
        CyclicBarrier barrier = new CyclicBarrier(students.length);
        try {
            List<Future<Throwable>> futures = new ArrayList<>();
            for (String s : students) {
                Callable<Throwable> task = () -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    try {
                        registrationService.register(s, courseId);
                        return null;
                    } catch (Throwable t) {
                        return t;
                    }
                };
                futures.add(pool.submit(task));
            }
            List<Throwable> out = new ArrayList<>();
            for (Future<Throwable> f : futures) {
                out.add(f.get(60, TimeUnit.SECONDS));
            }
            return out;
        } finally {
            pool.shutdownNow();
        }
    }

    private static long successes(List<Throwable> outcomes) {
        return outcomes.stream().filter(t -> t == null).count();
    }

    private static void assertAll(List<Throwable> outcomes, Class<?>... allowed) {
        for (Throwable t : outcomes) {
            if (t != null) {
                assertThat(t).isInstanceOfAny(allowed);
            }
        }
    }

    @Test
    @DisplayName("FR3-EX12: two students racing for the last seat, 20 rounds: exactly one wins and seats end at 0")
    void lastSeatRace() throws Exception {
        for (int round = 1; round <= 20; round++) {
            Long id = createCourse("LAST1", 1);
            try {
                List<Throwable> outcomes = race(id, ALICE, BOB);
                assertThat(successes(outcomes)).as("round %d successes", round).isEqualTo(1);
                assertAll(outcomes, CourseFullException.class, RegistrationConflictException.class);
                assertThat(seats(id)).as("round %d seats", round).isZero();
                assertThat(regs(id)).as("round %d registrations", round).isEqualTo(1);
                assertThat(audits(id)).as("round %d audit rows", round).isEqualTo(1);
            } finally {
                removeCourse(id);
            }
        }
    }

    @Test
    @DisplayName("FR3-EX13: three students for two seats: exactly two succeed and seats never go negative")
    void noOverselling() throws Exception {
        Long id = createCourse("TWO2", 2);
        try {
            List<Throwable> outcomes = race(id, ALICE, BOB, CAROL);
            assertThat(successes(outcomes)).isEqualTo(2);
            assertAll(outcomes, CourseFullException.class, RegistrationConflictException.class);
            assertThat(seats(id)).isZero();
            assertThat(regs(id)).isEqualTo(2);
            assertThat(audits(id)).isEqualTo(2);
        } finally {
            removeCourse(id);
        }
    }

    @Test
    @DisplayName("FR3-EX14: the same student double-submitting registers once")
    void doubleSubmit() throws Exception {
        Long id = createCourse("DBL50", 50);
        try {
            List<Throwable> outcomes = race(id, ALICE, ALICE);
            assertThat(successes(outcomes)).isEqualTo(1);
            assertAll(outcomes, AlreadyRegisteredException.class, RegistrationConflictException.class);
            assertThat(seats(id)).isEqualTo(49);
            assertThat(regs(id)).isEqualTo(1);
            assertThat(audits(id)).isEqualTo(1);
        } finally {
            removeCourse(id);
        }
    }
}
