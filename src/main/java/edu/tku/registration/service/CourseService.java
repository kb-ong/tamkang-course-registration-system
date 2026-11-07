package edu.tku.registration.service;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.tku.registration.domain.Course;
import edu.tku.registration.domain.Semester;
import edu.tku.registration.repository.CourseRepository;
import edu.tku.registration.repository.RegistrationRepository;
import edu.tku.registration.repository.SemesterRepository;

@Service
public class CourseService {

    private final CourseRepository courseRepository;
    private final SemesterRepository semesterRepository;
    private final RegistrationRepository registrationRepository;
    private final SemesterService semesterService;

    public CourseService(CourseRepository courseRepository, SemesterRepository semesterRepository,
                         RegistrationRepository registrationRepository, SemesterService semesterService) {
        this.courseRepository = courseRepository;
        this.semesterRepository = semesterRepository;
        this.registrationRepository = registrationRepository;
        this.semesterService = semesterService;
    }

    @Transactional(readOnly = true)
    public List<SemesterOption> listSemesters() {
        return semesterRepository.findAllByOrderByCodeDesc().stream().map(CourseService::toOption).toList();
    }

    @Transactional(readOnly = true)
    public CourseListing searchCourses(String studentId, String semesterCode, String query) {
        Semester semester;
        if (semesterCode == null || semesterCode.isBlank()) {
            semester = semesterRepository.findByCurrentTrue().orElse(null);
            if (semester == null) {
                return new CourseListing(null, null, List.of());
            }
        } else {
            String code = semesterCode.trim();
            semester = semesterRepository.findByCode(code)
                    .orElseThrow(() -> new SemesterNotFoundException(code));
        }

        String q = query == null ? "" : query.trim();
        List<Course> courses;
        if (q.isEmpty()) {
            courses = courseRepository.findBySemester_CodeOrderByCourseCodeAsc(semester.getCode());
        } else {
            String escaped = q.toLowerCase(Locale.ROOT)
                    .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            courses = courseRepository.search(semester.getCode(), "%" + escaped + "%");
        }

        Set<Long> registered = new HashSet<>(
                registrationRepository.findRegisteredCourseIds(studentId, semester.getCode()));
        List<CourseSummary> summaries = courses.stream()
                .map(c -> new CourseSummary(c.getId(), c.getCourseCode(), c.getCourseName(), c.getCreditHours(),
                        c.getInstructor(), c.getCapacity(), c.getAvailableSeats(), registered.contains(c.getId())))
                .toList();
        return new CourseListing(toOption(semester), semesterService.getRegistrationPeriod(semester.getCode()), summaries);
    }

    private static SemesterOption toOption(Semester s) {
        return new SemesterOption(s.getCode(), s.getName(), s.isCurrent());
    }
}
