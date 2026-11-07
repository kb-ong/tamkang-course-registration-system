package edu.tku.registration.service;

import java.time.LocalDateTime;

public record RegistrationResult(Long registrationId, Long courseId, String courseCode,
                                 String courseName, int availableSeats, LocalDateTime registrationDate) { }
