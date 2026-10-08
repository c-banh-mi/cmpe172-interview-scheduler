package edu.sjsu.cmpe172.scheduler.exception;

/** The slot is already booked (possibly by a concurrent request), removed, or in the past. Mapped to 409. */
public class SlotUnavailableException extends ConflictException {
    public SlotUnavailableException(String message) {
        super(message);
    }
}
