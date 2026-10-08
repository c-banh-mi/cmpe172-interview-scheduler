package edu.sjsu.cmpe172.scheduler.model;

import java.time.LocalDateTime;

/** An appointments row joined with its slot, service, provider and customer. */
public record AppointmentView(
        long id,
        long slotId,
        String serviceName,
        String providerName,
        String customerName,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String status,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime cancelledAt) {
}
