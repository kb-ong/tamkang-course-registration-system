package edu.tku.registration.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import edu.tku.registration.domain.Course;

public interface CourseRepository extends JpaRepository<Course, Long> {

    Optional<Course> findByCourseCodeAndSemester_Code(String courseCode, String semesterCode);

    List<Course> findBySemester_CodeOrderByCourseCodeAsc(String semesterCode);

    /** {@code pattern} must already be lower-cased, wrapped in % and escaped with backslash. */
    @Query("""
            select c from Course c
            where c.semester.code = :semesterCode
              and (lower(c.courseCode) like :pattern escape '\\'
                   or lower(c.courseName) like :pattern escape '\\')
            order by c.courseCode asc
            """)
    List<Course> search(@Param("semesterCode") String semesterCode, @Param("pattern") String pattern);
}
