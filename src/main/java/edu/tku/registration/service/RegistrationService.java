package edu.tku.registration.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import edu.tku.registration.domain.AuditAction;
import edu.tku.registration.domain.AuditLog;
import edu.tku.registration.domain.Course;
import edu.tku.registration.domain.Registration;
import edu.tku.registration.domain.Student;
import edu.tku.registration.repository.AuditLogRepository;
import edu.tku.registration.repository.CourseRepository;
import edu.tku.registration.repository.RegistrationRepository;
import edu.tku.registration.repository.StudentRepository;

@Service
public class RegistrationService {

    static final int MAX_ATTEMPTS = 5;

    private final StudentRepository studentRepository;
    private final CourseRepository courseRepository;
    private final RegistrationRepository registrationRepository;
    private final AuditLogRepository auditLogRepository;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate readOnlyTemplate;
    private final Clock clock;

    public RegistrationService(StudentRepository studentRepository, CourseRepository courseRepository,
                               RegistrationRepository registrationRepository,
                               AuditLogRepository auditLogRepository,
                               PlatformTransactionManager transactionManager, Clock clock) {
        this.studentRepository = studentRepository;
        this.courseRepository = courseRepository;
        this.registrationRepository = registrationRepository;
        this.auditLogRepository = auditLogRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.readOnlyTemplate = new TransactionTemplate(transactionManager);
        this.readOnlyTemplate.setReadOnly(true);
        this.clock = clock;
    }

    /** Not @Transactional: each attempt gets its own transaction so a lock conflict can be retried. */
    public RegistrationResult register(String studentId, Long courseId) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return transactionTemplate.execute(status -> attemptRegister(studentId, courseId));
            } catch (ConcurrencyFailureException ex) {
                // retry with a fresh transaction and fresh read
            }
        }
        if (courseId != null) {
            Course latest = readOnlyTemplate.execute(status -> courseRepository.findById(courseId).orElse(null));
            if (latest != null && latest.getAvailableSeats() <= 0) {
                throw new CourseFullException(latest.getCourseCode());
            }
        }
        throw new RegistrationConflictException();
    }

    private RegistrationResult attemptRegister(String studentId, Long courseId) {
        Student student = studentRepository.findByStudentId(studentId)
                .orElseThrow(() -> new StudentNotFoundException(studentId));
        Course course = (courseId == null ? java.util.Optional.<Course>empty() : courseRepository.findById(courseId))
                .orElseThrow(() -> new CourseNotFoundException(courseId));
        if (registrationRepository.existsByStudent_IdAndCourse_Id(student.getId(), course.getId())) {
            throw new AlreadyRegisteredException(course.getCourseCode());
        }
        if (course.getAvailableSeats() <= 0) {
            throw new CourseFullException(course.getCourseCode());
        }
        course.setAvailableSeats(course.getAvailableSeats() - 1);
        courseRepository.saveAndFlush(course);

        LocalDateTime now = LocalDateTime.now(clock);
        Registration registration;
        try {
            registration = registrationRepository.saveAndFlush(new Registration(student, course, now));
        } catch (DataIntegrityViolationException ex) {
            throw new AlreadyRegisteredException(course.getCourseCode());
        }
        auditLogRepository.save(new AuditLog(studentId, course.getId(), AuditAction.REGISTER, now));
        return new RegistrationResult(registration.getId(), course.getId(), course.getCourseCode(),
                course.getCourseName(), course.getAvailableSeats(), now);
    }
}
