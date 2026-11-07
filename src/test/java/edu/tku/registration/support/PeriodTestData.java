package edu.tku.registration.support;

import edu.tku.registration.domain.Course;
import edu.tku.registration.domain.RegistrationPeriod;
import edu.tku.registration.domain.Semester;
import edu.tku.registration.repository.CourseRepository;
import edu.tku.registration.repository.RegistrationPeriodRepository;
import edu.tku.registration.repository.SemesterRepository;
import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;

/** Creates and removes throw-away semesters / periods / courses for the FR7 and FR4 tests. Not a test class. */
public final class PeriodTestData {

    private PeriodTestData() {
    }

    /** start == null means "no RegistrationPeriod row". Not current. */
    public static Semester semester(SemesterRepository semesters, RegistrationPeriodRepository periods,
                                    String code, LocalDateTime start, LocalDateTime end) {
        Semester s = semesters.saveAndFlush(new Semester(code, "Test " + code, false));
        if (start != null) {
            periods.saveAndFlush(new RegistrationPeriod(s, start, end));
        }
        return s;
    }

    public static Course course(CourseRepository courses, Semester s, String code, int capacity, int seats) {
        return courses.saveAndFlush(new Course(code, "Test " + code, 3, "Prof. Test", capacity, seats, s));
    }

    /** For non-transactional tests: removes everything attached to the given semester codes. */
    public static void removeSemesters(JdbcTemplate jdbc, String... codes) {
        for (String code : codes) {
            String ids = "(select c.id from course c join semester s on s.id = c.semester_id where s.code = ?)";
            jdbc.update("delete from audit_log where course_id in " + ids, code);
            jdbc.update("delete from registration where course_id in " + ids, code);
            jdbc.update("delete from course where semester_id in (select id from semester where code = ?)", code);
            jdbc.update("delete from registration_period where semester_id in (select id from semester where code = ?)",
                    code);
            jdbc.update("delete from semester where code = ?", code);
        }
    }
}
