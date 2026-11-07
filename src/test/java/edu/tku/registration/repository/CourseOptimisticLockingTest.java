package edu.tku.registration.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.tku.registration.domain.Course;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/**
 * Deliberately NOT @Transactional (spec SK-AC4): each repository call runs in its own
 * transaction so the two loaded instances are detached and the version check hits the DB.
 * The seed value is restored in @AfterEach so other test classes that share this cached
 * context (and therefore its in-memory database) still see the seed data.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CourseOptimisticLockingTest {

    private static final int SEED_CS101_SEATS = 50;

    @Autowired
    private CourseRepository courseRepository;

    @AfterEach
    void restoreSeed() {
        Course fresh = courseRepository.findByCourseCodeAndSemester_Code("CS101", "115-1").orElseThrow();
        fresh.setAvailableSeats(SEED_CS101_SEATS);
        courseRepository.saveAndFlush(fresh);
    }

    @Test
    @DisplayName("SK-AC4: saving a stale Course instance throws ObjectOptimisticLockingFailureException")
    void staleUpdateIsRejected() {
        Course a = courseRepository.findByCourseCodeAndSemester_Code("CS101", "115-1").orElseThrow();
        Course b = courseRepository.findByCourseCodeAndSemester_Code("CS101", "115-1").orElseThrow();
        assertThat(a).isNotSameAs(b);

        a.setAvailableSeats(a.getAvailableSeats() - 1);
        courseRepository.saveAndFlush(a);

        b.setAvailableSeats(b.getAvailableSeats() - 1);
        assertThatThrownBy(() -> courseRepository.saveAndFlush(b))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }
}
