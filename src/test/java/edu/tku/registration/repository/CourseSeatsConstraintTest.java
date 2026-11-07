package edu.tku.registration.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.tku.registration.domain.Course;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** FR6-EX3: the database refuses availableSeats < 0 or > capacity. Rolled back after each test. */
@SpringBootTest
@Transactional
class CourseSeatsConstraintTest {

    @Autowired private CourseRepository courseRepository;
    @Autowired private SemesterRepository semesterRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;

    private Course cs101() {
        return courseRepository.findByCourseCodeAndSemester_Code("CS101", "115-1").orElseThrow();
    }

    private int jdbcUpdateSeats(int seats) {
        return jdbc.update("update course set available_seats = ? where course_code = 'CS101' "
                + "and semester_id = (select id from semester where code = '115-1')", seats);
    }

    @Test
    @DisplayName("FR6-EX3: JDBC UPDATE to -1 seats or to more than capacity is rejected")
    void jdbcRejects() {
        assertThatThrownBy(() -> jdbcUpdateSeats(-1)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcUpdateSeats(51)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("FR6-EX3: saveAndFlush with -1 seats is rejected")
    void jpaRejectsNegative() {
        Course c = cs101();
        c.setAvailableSeats(-1);
        assertThatThrownBy(() -> courseRepository.saveAndFlush(c)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("FR6-EX3: saveAndFlush with seats above capacity (51 for CS101) is rejected")
    void jpaRejectsAboveCapacity() {
        Course c = cs101();
        c.setAvailableSeats(51);
        assertThatThrownBy(() -> courseRepository.saveAndFlush(c)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("FR6-EX3: seats 0 and seats = capacity are accepted")
    void boundariesAccepted() {
        assertThat(jdbcUpdateSeats(0)).isEqualTo(1);
        assertThat(jdbcUpdateSeats(50)).isEqualTo(1);
        Course c = cs101();
        c.setAvailableSeats(0);
        courseRepository.saveAndFlush(c);
        c.setAvailableSeats(c.getCapacity());
        courseRepository.saveAndFlush(c);
        entityManager.clear();
        assertThat(cs101().getAvailableSeats()).isEqualTo(50);
    }

    @Test
    @DisplayName("FR6-EX3: creating a course with capacity 1 and 2 available seats is rejected")
    void createAboveCapacityRejected() {
        Course bad = new Course("BAD01", "Bad", 3, "Prof. Test", 1, 2,
                semesterRepository.findByCode("115-1").orElseThrow());
        assertThatThrownBy(() -> courseRepository.saveAndFlush(bad)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
