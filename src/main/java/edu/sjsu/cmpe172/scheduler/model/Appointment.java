package edu.sjsu.cmpe172.scheduler.model;

import java.time.LocalDateTime;

/** Row of appointments, plus its slot's start time (needed for the "no cancelling the past" rule). */
public record Appointment(long id, long slotId, long customerId, String status, LocalDateTime startTime) {

    public static final String BOOKED = "BOOKED";
    public static final String CANCELLED = "CANCELLED";
    public static final String COMPLETED = "COMPLETED";
}
