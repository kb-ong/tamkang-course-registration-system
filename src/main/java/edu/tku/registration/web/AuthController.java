package edu.tku.registration.web;

import java.security.Principal;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.tku.registration.service.StudentProfile;
import edu.tku.registration.service.StudentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final CsrfTokenRepository csrfTokenRepository;
    private final StudentService studentService;

    public AuthController(AuthenticationManager authenticationManager,
                          SecurityContextRepository securityContextRepository,
                          CsrfTokenRepository csrfTokenRepository,
                          StudentService studentService) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.csrfTokenRepository = csrfTokenRepository;
        this.studentService = studentService;
    }

    /** Authenticates with student ID and password; AuthenticationException is mapped to 401 by ApiExceptionHandler. */
    @PostMapping("/login")
    public StudentProfile login(@RequestBody LoginRequest body,
                                HttpServletRequest request,
                                HttpServletResponse response) {
        String studentId = body.studentId() != null ? body.studentId() : "";
        String password = body.password() != null ? body.password() : "";
        Authentication auth = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(studentId, password));

        // Session fixation protection
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }

        SecurityContextHolderStrategy strategy = SecurityContextHolder.getContextHolderStrategy();
        SecurityContext context = strategy.createEmptyContext();
        context.setAuthentication(auth);
        strategy.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        // Invalidate the pre-login CSRF token; the client fetches a fresh one lazily
        csrfTokenRepository.saveToken(null, request, response);

        return studentService.getProfile(auth.getName());
    }

    @GetMapping("/me")
    public StudentProfile me(Principal principal) {
        return studentService.getProfile(principal.getName());
    }

    /** CSRF bootstrap: loading the token makes the repository write the XSRF-TOKEN cookie if absent. */
    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf(CsrfToken token) {
        token.getToken();
        return ResponseEntity.noContent().build();
    }
}
