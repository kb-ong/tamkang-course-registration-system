package edu.tku.registration.service;

public class CourseNotFoundException extends RuntimeException {

    public CourseNotFoundException(Long courseId) {
        super("Course not found.");
    }
}
