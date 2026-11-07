package edu.tku.registration.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import edu.tku.registration.domain.Course;

public interface CourseRepository extends JpaRepository<Course, Long> {

    Optional<Course> findByCourseCodeAndSemester_Code(String courseCode, String semesterCode);
}
