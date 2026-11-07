package edu.tku.registration.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.tku.registration.domain.Semester;
import edu.tku.registration.repository.RegistrationRepository;
import edu.tku.registration.repository.SemesterRepository;
import edu.tku.registration.repository.StudentRepository;

@Service
public class ScheduleService {

    private final StudentRepository studentRepository;
    private final SemesterRepository semesterRepository;
    private final RegistrationRepository registrationRepository;
    private final SemesterService semesterService;

    public ScheduleService(StudentRepository studentRepository, SemesterRepository semesterRepository,
                           RegistrationRepository registrationRepository, SemesterService semesterService) {
        this.studentRepository = studentRepository;
        this.semesterRepository = semesterRepository;
        this.registrationRepository = registrationRepository;
        this.semesterService = semesterService;
    }

    @Transactional(readOnly = true)
    public RegistrationSchedule getSchedule(String studentId, String semesterCode) {
        studentRepository.findByStudentId(studentId).orElseThrow(() -> new StudentNotFoundException(studentId));
        List<SemesterOption> semesters = semesterRepository.findAllByOrderByCodeDesc().stream()
                .map(ScheduleService::toOption).toList();

        Semester semester;
        if (semesterCode == null || semesterCode.isBlank()) {
            semester = semesterRepository.findByCurrentTrue().orElse(null);
            if (semester == null) {
                return new RegistrationSchedule(null, semesters, null, List.of(), 0);
            }
        } else {
            String code = semesterCode.trim();
            semester = semesterRepository.findByCode(code).orElseThrow(() -> new SemesterNotFoundException(code));
        }

        List<RegisteredCourse> courses = registrationRepository
                .findScheduleRows(studentId, semester.getCode()).stream()
                .map(r -> new RegisteredCourse(r.getId(), r.getCourse().getId(), r.getCourse().getCourseCode(),
                        r.getCourse().getCourseName(), r.getCourse().getCreditHours(),
                        r.getCourse().getInstructor(), r.getCourse().getSemester().getCode(),
                        r.getRegistrationDate()))
                .toList();
        int total = courses.stream().mapToInt(RegisteredCourse::credits).sum();
        return new RegistrationSchedule(toOption(semester), semesters,
                semesterService.getRegistrationPeriod(semester.getCode()), courses, total);
    }

    private static SemesterOption toOption(Semester s) {
        return new SemesterOption(s.getCode(), s.getName(), s.isCurrent());
    }
}
