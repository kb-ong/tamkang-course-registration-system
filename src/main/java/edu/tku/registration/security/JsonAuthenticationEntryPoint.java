package edu.tku.registration.security;

import java.io.IOException;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Answers unauthenticated API requests with 401 JSON instead of a redirect. */
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    static final String MESSAGE = "Authentication required.";

    private final ObjectMapper objectMapper;

    public JsonAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        JsonErrorWriter.write(objectMapper, response, HttpServletResponse.SC_UNAUTHORIZED, MESSAGE);
    }
}
