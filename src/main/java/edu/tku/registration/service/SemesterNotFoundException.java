package edu.tku.registration.service;

public class SemesterNotFoundException extends RuntimeException {

    public SemesterNotFoundException(String code) {
        super("Semester not found.");
    }
}
