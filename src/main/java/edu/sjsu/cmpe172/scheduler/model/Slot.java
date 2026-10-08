package edu.sjsu.cmpe172.scheduler.model;

import java.time.LocalDateTime;

/** Row of availability_slots, including the optimistic-lock version used by the booking path. */
public record Slot(
        long id,
        long providerId,
        long serviceId,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String status,
        int version) {

    public static final String OPEN = "OPEN";
    public static final String BOOKED = "BOOKED";
    public static final String REMOVED = "REMOVED";
}
