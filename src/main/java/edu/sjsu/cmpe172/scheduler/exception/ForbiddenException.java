package edu.sjsu.cmpe172.scheduler.exception;

/** Logged in, but not allowed to do this (wrong role, or not the owner). Mapped to 403. */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
