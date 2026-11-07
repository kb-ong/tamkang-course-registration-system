package edu.tku.registration.service;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

public record RegisteredCourse(Long registrationId, Long courseId, String code, String name, int credits,
        String instructor, String semesterCode,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime registeredAt) { }
