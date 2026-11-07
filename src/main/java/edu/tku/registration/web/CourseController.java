package edu.tku.registration.web;

import java.security.Principal;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import edu.tku.registration.service.CourseListing;
import edu.tku.registration.service.CourseService;
import edu.tku.registration.service.SemesterOption;

@RestController
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @GetMapping("/api/semesters")
    public List<SemesterOption> semesters() {
        return courseService.listSemesters();
    }

    @GetMapping("/api/courses")
    public CourseListing courses(@RequestParam(required = false) String semester,
                                 @RequestParam(required = false) String q,
                                 Principal principal) {
        return courseService.searchCourses(principal.getName(), semester, q);
    }
}
