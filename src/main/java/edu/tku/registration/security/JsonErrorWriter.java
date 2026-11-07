package edu.tku.registration.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletResponse;

/** Writes {@code {"message": "..."}} JSON error bodies from security filters. */
final class JsonErrorWriter {

    private JsonErrorWriter() {
    }

    static void write(ObjectMapper objectMapper, HttpServletResponse response, int status, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), Map.of("message", message));
    }
}
