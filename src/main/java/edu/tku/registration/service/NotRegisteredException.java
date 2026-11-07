package edu.tku.registration.service;

public class NotRegisteredException extends RuntimeException {

    public NotRegisteredException(String courseCode) {
        super("You are not registered for " + courseCode + ".");
    }
}
