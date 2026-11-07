package edu.tku.registration.web;

import java.security.Principal;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import edu.tku.registration.service.RegistrationResult;
import edu.tku.registration.service.DropResult;
import edu.tku.registration.service.RegistrationSchedule;
import edu.tku.registration.service.RegistrationService;
import edu.tku.registration.service.ScheduleService;

@RestController
public class RegistrationController {

    private final RegistrationService registrationService;
    private final ScheduleService scheduleService;

    public RegistrationController(RegistrationService registrationService, ScheduleService scheduleService) {
        this.registrationService = registrationService;
        this.scheduleService = scheduleService;
    }

    @PostMapping("/api/registrations")
    public ResponseEntity<?> register(@RequestBody RegistrationRequest request, Principal principal) {
        if (request == null || request.courseId() == null) {
            return ResponseEntity.badRequest().body(new ApiError("Course ID is required."));
        }
        RegistrationResult r = registrationService.register(principal.getName(), request.courseId());
        return ResponseEntity.status(HttpStatus.CREATED).body(new RegistrationResponse(
                "Successfully registered for " + r.courseCode() + ".",
                r.registrationId(), r.courseId(), r.courseCode(), r.availableSeats()));
    }

    @GetMapping("/api/registrations")
    public RegistrationSchedule list(@RequestParam(required = false) String semester, Principal principal) {
        return scheduleService.getSchedule(principal.getName(), semester);
    }

    @DeleteMapping("/api/registrations/{courseId}")
    public ResponseEntity<DropResponse> drop(@PathVariable Long courseId, Principal principal) {
        DropResult r = registrationService.drop(principal.getName(), courseId);
        return ResponseEntity.ok(new DropResponse("Successfully dropped " + r.courseCode() + ".",
                r.courseId(), r.courseCode(), r.availableSeats()));
    }
}
