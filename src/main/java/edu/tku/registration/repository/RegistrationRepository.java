package edu.tku.registration.repository;

import java.util.List;

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
}
