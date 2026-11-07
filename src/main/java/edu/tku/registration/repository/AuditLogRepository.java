package edu.tku.registration.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import edu.tku.registration.domain.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
