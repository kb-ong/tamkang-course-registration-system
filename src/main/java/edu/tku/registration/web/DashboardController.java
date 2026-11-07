package edu.tku.registration.web;

import java.security.Principal;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.tku.registration.service.SemesterService;
import edu.tku.registration.service.StudentService;

@RestController
public class DashboardController {

    private final StudentService studentService;
    private final SemesterService semesterService;

    public DashboardController(StudentService studentService, SemesterService semesterService) {
        this.studentService = studentService;
        this.semesterService = semesterService;
    }

    @GetMapping("/api/dashboard")
    public DashboardResponse dashboard(Principal principal) {
        return new DashboardResponse(
                studentService.getProfile(principal.getName()),
                semesterService.getCurrentSemesterInfo().orElse(null));
    }
}
