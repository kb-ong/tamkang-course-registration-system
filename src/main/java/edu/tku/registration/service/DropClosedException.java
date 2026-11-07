package edu.tku.registration.service;

public class DropClosedException extends RuntimeException {

    public DropClosedException(String semesterCode) {
        super("Dropping is closed for semester " + semesterCode + ".");
    }
}
