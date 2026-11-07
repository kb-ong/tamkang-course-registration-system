package edu.tku.registration.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import edu.tku.registration.domain.Semester;

public interface SemesterRepository extends JpaRepository<Semester, Long> {

    Optional<Semester> findByCode(String code);

    Optional<Semester> findByCurrentTrue();

    List<Semester> findAllByOrderByCodeDesc();
}
