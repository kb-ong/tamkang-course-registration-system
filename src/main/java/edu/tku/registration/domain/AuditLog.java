package edu.tku.registration.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** University student ID (plain column, not a FK). */
    @Column(nullable = false, length = 9)
    private String studentId;

    /** Course primary key (plain column, not a FK). */
    @Column(nullable = false)
    private Long courseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuditAction action;

    @Column(name = "action_timestamp", nullable = false)
    private LocalDateTime timestamp;

    protected AuditLog() {
    }

    public AuditLog(String studentId, Long courseId, AuditAction action, LocalDateTime timestamp) {
        this.studentId = studentId;
        this.courseId = courseId;
        this.action = action;
        this.timestamp = timestamp;
    }

    public Long getId() {
        return id;
    }

    public String getStudentId() {
        return studentId;
    }

    public Long getCourseId() {
        return courseId;
    }

    public AuditAction getAction() {
        return action;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }
}
