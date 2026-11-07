package edu.tku.registration.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import edu.tku.registration.service.AlreadyRegisteredException;
import edu.tku.registration.service.CourseFullException;
import edu.tku.registration.service.CourseNotFoundException;
import edu.tku.registration.service.DropClosedException;
import edu.tku.registration.service.NotRegisteredException;
import edu.tku.registration.service.RegistrationClosedException;
import edu.tku.registration.service.RegistrationConflictException;
import edu.tku.registration.service.SemesterNotFoundException;
import edu.tku.registration.service.StudentNotFoundException;

/** Turns exceptions into {@code {"message": "..."}} JSON bodies. Never exposes stack traces. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex) {
        return error(HttpStatus.UNAUTHORIZED, "Invalid student ID or password.");
    }

    @ExceptionHandler(StudentNotFoundException.class)
    public ResponseEntity<ApiError> handleStudentNotFound(StudentNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "Student not found.");
    }

    @ExceptionHandler({SemesterNotFoundException.class, CourseNotFoundException.class,
            NotRegisteredException.class})
    public ResponseEntity<ApiError> handleNotFound(RuntimeException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler({AlreadyRegisteredException.class, CourseFullException.class,
            RegistrationConflictException.class,
            RegistrationClosedException.class, DropClosedException.class})
    public ResponseEntity<ApiError> handleConflict(RuntimeException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return error(HttpStatus.BAD_REQUEST, "Malformed request.");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleNotReadable(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "Malformed request.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleOther(Exception ex) {
        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatusCode status = errorResponse.getStatusCode();
            String message = switch (status.value()) {
                case 404 -> "Not found.";
                case 405 -> "Method not allowed.";
                case 415 -> "Unsupported media type.";
                default -> "Request could not be processed.";
            };
            return error(status, message);
        }
        log.error("Unhandled exception", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
    }

    private static ResponseEntity<ApiError> error(HttpStatusCode status, String message) {
        return ResponseEntity.status(status).body(new ApiError(message));
    }
}
