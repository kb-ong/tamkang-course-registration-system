package edu.tku.registration.service;

public class RegistrationConflictException extends RuntimeException {

    public RegistrationConflictException() {
        super("The course was just updated by another student. Please try again.");
    }
}
