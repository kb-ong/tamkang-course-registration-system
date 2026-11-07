package edu.tku.registration.service;

import edu.tku.registration.domain.Course;
import edu.tku.registration.repository.CourseRepository;
import edu.tku.registration.repository.SemesterRepository;
import org.springframework.jdbc.core.JdbcTemplate;

/** Helpers shared by the FR6 tests: create/remove throw-away courses and count rows (not a test class). */
public final class CapacityTestData {

    private CapacityTestData() {
    }

    public static Long createCourse(CourseRepository courses, SemesterRepository semesters,
                                    String code, int capacity, int seats) {
        Course c = new Course(code, "FR6 Test " + code, 3, "Prof. Test", capacity, seats,
                semesters.findByCode("115-1").orElseThrow());
        return courses.saveAndFlush(c).getId();
    }

    public static void removeCourse(JdbcTemplate jdbc, Long id) {
        jdbc.update("delete from audit_log where course_id = ?", id);
        jdbc.update("delete from registration where course_id = ?", id);
        jdbc.update("delete from course where id = ?", id);
    }

    public static int seats(JdbcTemplate jdbc, Long id) {
        return jdbc.queryForObject("select available_seats from course where id = ?", Integer.class, id);
    }

    public static long version(JdbcTemplate jdbc, Long id) {
        return jdbc.queryForObject("select version from course where id = ?", Long.class, id);
    }

    public static int regs(JdbcTemplate jdbc, Long id) {
        return jdbc.queryForObject("select count(*) from registration where course_id = ?", Integer.class, id);
    }

    public static int audits(JdbcTemplate jdbc, Long id) {
        return jdbc.queryForObject("select count(*) from audit_log where course_id = ?", Integer.class, id);
    }

    public static int totalRegs(JdbcTemplate jdbc) {
        return jdbc.queryForObject("select count(*) from registration", Integer.class);
    }

    public static int totalAudits(JdbcTemplate jdbc) {
        return jdbc.queryForObject("select count(*) from audit_log", Integer.class);
    }

    /** Spec invariant: availableSeats + registrations = capacity. */
    public static int invariantGap(JdbcTemplate jdbc, Long id) {
        return jdbc.queryForObject("""
                select c.capacity - c.available_seats
                       - (select count(*) from registration r where r.course_id = c.id)
                from course c where c.id = ?""", Integer.class, id);
    }
}
