package edu.tku.registration.web;

import java.security.Principal;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import edu.tku.registration.service.RegistrationResult;
import edu.tku.registration.service.RegistrationService;

@RestController
public class RegistrationController {

    private final RegistrationService registrationService;

    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
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
}
