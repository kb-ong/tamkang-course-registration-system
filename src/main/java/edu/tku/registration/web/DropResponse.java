package edu.tku.registration.web;

public record DropResponse(String message, Long courseId, String courseCode, int availableSeats) { }
