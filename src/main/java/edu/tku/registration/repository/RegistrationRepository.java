package edu.tku.registration.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import edu.tku.registration.domain.Registration;

public interface RegistrationRepository extends JpaRepository<Registration, Long> {
}
