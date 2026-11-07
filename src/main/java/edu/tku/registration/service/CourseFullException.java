package edu.tku.registration.service;

public class CourseFullException extends RuntimeException {

    public CourseFullException(String courseCode) {
        super("Course " + courseCode + " is full.");
    }
}
