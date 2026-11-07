package edu.tku.registration.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.tku.registration.domain.Student;
import edu.tku.registration.repository.StudentRepository;

@Service
public class StudentService {

    private final StudentRepository studentRepository;

    public StudentService(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    /**
     * Returns the profile of the student with the given university student ID.
     *
     * @throws StudentNotFoundException if no student has that ID
     */
    @Transactional(readOnly = true)
    public StudentProfile getProfile(String studentId) {
        Student student = studentRepository.findByStudentId(studentId)
                .orElseThrow(() -> new StudentNotFoundException(studentId));
        return new StudentProfile(student.getStudentId(), student.getName(), student.getEmail());
    }
}
