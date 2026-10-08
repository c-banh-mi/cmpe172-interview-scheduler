package edu.sjsu.cmpe172.scheduler.exception;

/** The request is valid but clashes with the current state of the data. Mapped to 409. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
