package edu.tku.registration.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import edu.tku.registration.domain.Registration;

public interface RegistrationRepository extends JpaRepository<Registration, Long> {

    @Query("""
            select r.course.id from Registration r
            where r.student.studentId = :studentId and r.course.semester.code = :semesterCode
            """)
    List<Long> findRegisteredCourseIds(@Param("studentId") String studentId,
                                       @Param("semesterCode") String semesterCode);

    boolean existsByStudent_IdAndCourse_Id(Long studentPk, Long courseId);

    Optional<Registration> findByStudent_IdAndCourse_Id(Long studentPk, Long courseId);

    @Query("""
            select r from Registration r join fetch r.course c join fetch c.semester s
            where r.student.studentId = :studentId and s.code = :semesterCode
            order by c.courseCode asc
            """)
    List<Registration> findScheduleRows(@Param("studentId") String studentId,
                                        @Param("semesterCode") String semesterCode);
}
