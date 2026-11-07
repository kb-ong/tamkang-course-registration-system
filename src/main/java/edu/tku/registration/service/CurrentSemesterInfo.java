package edu.tku.registration.service;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * The current semester and its registration period, as shown on the dashboard.
 * Date-times are Asia/Taipei local time; both are {@code null} when the semester has no period.
 */
public record CurrentSemesterInfo(
        String code,
        String name,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime registrationStartAt,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime registrationEndAt,
        boolean registrationOpen) {
}
