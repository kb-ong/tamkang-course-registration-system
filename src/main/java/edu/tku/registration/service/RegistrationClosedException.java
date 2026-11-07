package edu.tku.registration.service;

public class RegistrationClosedException extends RuntimeException {

    public RegistrationClosedException(String semesterCode) {
        super("Registration is closed for semester " + semesterCode + ".");
    }
}
