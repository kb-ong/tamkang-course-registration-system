package edu.tku.registration.service;

public record DropResult(Long courseId, String courseCode, String courseName, int availableSeats) { }
