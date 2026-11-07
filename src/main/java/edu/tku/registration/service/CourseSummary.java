package edu.tku.registration.service;

public record CourseSummary(Long id, String code, String name, int credits, String instructor,
                            int capacity, int availableSeats, boolean registered) { }
