package edu.tku.registration.web;

public record RegistrationResponse(String message, Long registrationId, Long courseId,
                                   String courseCode, int availableSeats) { }
