package edu.tku.registration.service;

import static edu.tku.registration.service.CapacityTestData.*;
import static org.assertj.core.api.Assertions.assertThat;

import edu.tku.registration.repository.CourseRepository;
import edu.tku.registration.repository.SemesterRepository;
import edu.tku.registration.support.MutableTestClock;
import edu.tku.registration.support.PinnedClockConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

/** FR4-EX9 / FR4-EX10: concurrent drops. Threads are released together by a CyclicBarrier. Not @Transactional. */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(PinnedClockConfig.class)
class DropConcurrencyTest {

    private static final String ALICE = "410000001";
    private static final String BOB = "410000002";

    @Autowired private RegistrationService registrationService;
    @Autowired private CourseRepository courseRepository;
    @Autowired private SemesterRepository semesterRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MutableTestClock clock;

    @BeforeEach
    void setUp() {
        clock.set(MutableTestClock.T_OPEN);
    }

    private int auditsOf(Long id, String action) {
        return jdbc.queryForObject(
                "select count(*) from audit_log where course_id = ? and action = ?", Integer.class, id, action);
    }

    /** Runs both tasks at the same moment; returns each thrown exception or null. */
    private List<Throwable> race(Runnable a, Runnable b) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        try {
            List<Future<Throwable>> futures = new ArrayList<>();
            for (Runnable r : List.of(a, b)) {
                Callable<Throwable> task = () -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    try {
                        r.run();
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

    @Test
    @DisplayName("FR4-EX9: Alice double-submitting a drop (20 rounds): one returns, the other fails, seats +1 once, one DROP row")
    void doubleSubmitDrop() throws Exception {
        for (int round = 1; round <= 20; round++) {
            Long id = createCourse(courseRepository, semesterRepository, "DDBL50", 50, 50);
            try {
                registrationService.register(ALICE, id);
                assertThat(seats(jdbc, id)).isEqualTo(49);

                List<Throwable> o = race(() -> registrationService.drop(ALICE, id),
                        () -> registrationService.drop(ALICE, id));

                long ok = o.stream().filter(t -> t == null).count();
                assertThat(ok).as("round %d successes (outcomes %s)", round, o).isEqualTo(1);
                for (Throwable t : o) {
                    if (t != null) {
                        assertThat(t).isInstanceOfAny(NotRegisteredException.class,
                                RegistrationConflictException.class);
                    }
                }
                assertThat(seats(jdbc, id)).as("round %d seats", round).isEqualTo(50);
                assertThat(regs(jdbc, id)).as("round %d registrations", round).isZero();
                assertThat(auditsOf(id, "DROP")).as("round %d DROP rows", round).isEqualTo(1);
            } finally {
                removeCourse(jdbc, id);
            }
        }
    }

    @Test
    @DisplayName("FR4-EX10: Alice's drop racing Bob's register on the last seat (20 rounds): invariants hold in every outcome")
    void dropRacingRegister() throws Exception {
        for (int round = 1; round <= 20; round++) {
            Long id = createCourse(courseRepository, semesterRepository, "DLAST1", 1, 1);
            try {
                registrationService.register(ALICE, id);
                assertThat(seats(jdbc, id)).isZero();

                List<Throwable> o = race(() -> registrationService.drop(ALICE, id),
                        () -> registrationService.register(BOB, id));
                Throwable aliceOutcome = o.get(0);
                Throwable bobOutcome = o.get(1);

                assertThat(aliceOutcome).as("round %d Alice's drop", round).isNull();
                if (bobOutcome != null) {
                    assertThat(bobOutcome).isInstanceOfAny(CourseFullException.class,
                            RegistrationConflictException.class);
                }
                int seats = seats(jdbc, id);
                assertThat(invariantGap(jdbc, id)).as("round %d invariant", round).isZero();
                assertThat(seats).as("round %d seats", round).isBetween(0, 1);
                assertThat(auditsOf(id, "DROP")).as("round %d DROP rows", round).isEqualTo(1);
                assertThat(auditsOf(id, "REGISTER")).as("round %d REGISTER rows", round)
                        .isEqualTo(1 + (bobOutcome == null ? 1 : 0));
            } finally {
                removeCourse(jdbc, id);
            }
        }
    }
}
