package edu.sjsu.cmpe172.scheduler.exception;

/** Mapped to 404 by {@link GlobalExceptionHandler}. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
