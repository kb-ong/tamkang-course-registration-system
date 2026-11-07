package edu.tku.registration.service;

public class AlreadyRegisteredException extends RuntimeException {

    public AlreadyRegisteredException(String courseCode) {
        super("You are already registered for " + courseCode + ".");
    }
}
