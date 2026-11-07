package edu.tku.registration.service;

public class StudentNotFoundException extends RuntimeException {

    public StudentNotFoundException(String studentId) {
        super("Student not found: " + studentId);
    }
}
