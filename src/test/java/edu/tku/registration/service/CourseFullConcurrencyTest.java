package edu.tku.registration.service;

import static edu.tku.registration.service.CapacityTestData.*;
import static org.assertj.core.api.Assertions.assertThat;

import edu.tku.registration.domain.Student;
import edu.tku.registration.repository.CourseRepository;
import edu.tku.registration.repository.SemesterRepository;
import edu.tku.registration.repository.StudentRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;

/**
 * FR6-AC3c / AC3d / EX5: 10 students (3 seed + 7 created here) race for the seats. Threads are
 * released together by a CyclicBarrier. Not @Transactional: every thread runs its own transactions.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(edu.tku.registration.support.PinnedClockConfig.class)
class CourseFullConcurrencyTest {

    @Autowired private RegistrationService registrationService;
    @Autowired private CourseRepository courseRepository;
    @Autowired private SemesterRepository semesterRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private JdbcTemplate jdbc;

    private final List<String> studentIds = new ArrayList<>();

    @BeforeEach
    void createStudents() {
        studentIds.clear();
        studentIds.addAll(List.of("410000001", "410000002", "410000003"));
        String hash = new BCryptPasswordEncoder().encode("Race-pass-1");
        for (int i = 1; i <= 7; i++) {
            String id = "49000000" + i;
            studentRepository.save(new Student(id, "Racer " + i, id + "@o365.tku.edu.tw", hash));
            studentIds.add(id);
        }
    }

    @AfterEach
    void deleteStudents() {
        jdbc.update("delete from audit_log where student_id like '49000000%'");
        jdbc.update("delete from registration where student_id in (select id from student where student_id like '49000000%')");
        jdbc.update("delete from student where student_id like '49000000%'");
    }

    private List<Throwable> race(Long courseId) throws Exception {
        int n = studentIds.size();
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CyclicBarrier barrier = new CyclicBarrier(n);
        try {
            List<Future<Throwable>> futures = new ArrayList<>();
            for (String s : studentIds) {
                Callable<Throwable> task = () -> {
                    barrier.await(20, TimeUnit.SECONDS);
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
                out.add(f.get(120, TimeUnit.SECONDS));
            }
            return out;
        } finally {
            pool.shutdownNow();
        }
    }

    private static long successes(List<Throwable> o) {
        return o.stream().filter(t -> t == null).count();
    }

    private static long full(List<Throwable> o) {
        return o.stream().filter(t -> t instanceof CourseFullException).count();
    }

    @Test
    @DisplayName("FR6-AC3c: 10 students race for 1 seat, 10 rounds: exactly 1 succeeds, 9 get CourseFullException, nothing else")
    void oneSeatRace() throws Exception {
        for (int round = 1; round <= 10; round++) {
            Long id = createCourse(courseRepository, semesterRepository, "RACE1", 1, 1);
            try {
                List<Throwable> outcomes = race(id);
                assertThat(successes(outcomes)).as("round %d successes", round).isEqualTo(1);
                assertThat(full(outcomes)).as("round %d CourseFullException (others: %s)", round, outcomes)
                        .isEqualTo(9);
                assertThat(seats(jdbc, id)).as("round %d seats", round).isZero();
                assertThat(regs(jdbc, id)).as("round %d registrations", round).isEqualTo(1);
                assertThat(audits(jdbc, id)).as("round %d audit rows", round).isEqualTo(1);
                // FR6-EX5
                assertThat(invariantGap(jdbc, id)).as("round %d invariant", round).isZero();
            } finally {
                removeCourse(jdbc, id);
            }
        }
    }

    @Test
    @DisplayName("FR6-AC3d: 10 students race for 3 seats: exactly 3 succeed, 7 get CourseFullException, seats end at 0")
    void threeSeatRace() throws Exception {
        Long id = createCourse(courseRepository, semesterRepository, "RACE3", 3, 3);
        try {
            List<Throwable> outcomes = race(id);
            assertThat(successes(outcomes)).isEqualTo(3);
            assertThat(full(outcomes)).as("CourseFullException (outcomes: %s)", outcomes).isEqualTo(7);
            assertThat(seats(jdbc, id)).isZero();
            assertThat(regs(jdbc, id)).isEqualTo(3);
            assertThat(audits(jdbc, id)).isEqualTo(3);
            // FR6-EX5
            assertThat(invariantGap(jdbc, id)).isZero();
        } finally {
            removeCourse(jdbc, id);
        }
    }
}
