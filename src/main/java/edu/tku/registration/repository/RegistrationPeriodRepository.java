package edu.tku.registration.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import edu.tku.registration.domain.RegistrationPeriod;

public interface RegistrationPeriodRepository extends JpaRepository<RegistrationPeriod, Long> {

    Optional<RegistrationPeriod> findBySemester_Code(String semesterCode);
}
