package edu.tku.registration.security;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.tku.registration.domain.Student;
import edu.tku.registration.repository.StudentRepository;

@Service
public class StudentUserDetailsService implements UserDetailsService {

    static final String ROLE_STUDENT = "STUDENT";

    private final StudentRepository studentRepository;

    public StudentUserDetailsService(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String studentId) throws UsernameNotFoundException {
        Student student = studentRepository.findByStudentId(studentId)
                .orElseThrow(() -> new UsernameNotFoundException("Student not found: " + studentId));
        return User.withUsername(student.getStudentId())
                .password(student.getPasswordHash())
                .roles(ROLE_STUDENT)
                .build();
    }
}
