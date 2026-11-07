package edu.tku.registration.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import edu.tku.registration.domain.Student;

public interface StudentRepository extends JpaRepository<Student, Long> {

    Optional<Student> findByStudentId(String studentId);
}
