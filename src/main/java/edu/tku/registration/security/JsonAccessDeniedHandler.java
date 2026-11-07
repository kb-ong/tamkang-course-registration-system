package edu.tku.registration.security;

import java.io.IOException;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Answers forbidden requests (including CSRF failures) with 403 JSON. */
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

    static final String CSRF_MESSAGE = "Invalid or missing CSRF token.";
    static final String DENIED_MESSAGE = "Access denied.";

    private final ObjectMapper objectMapper;

    public JsonAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        String message = accessDeniedException instanceof CsrfException ? CSRF_MESSAGE : DENIED_MESSAGE;
        JsonErrorWriter.write(objectMapper, response, HttpServletResponse.SC_FORBIDDEN, message);
    }
}
