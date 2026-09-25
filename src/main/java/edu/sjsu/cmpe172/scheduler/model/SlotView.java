package edu.sjsu.cmpe172.scheduler.model;

import java.time.LocalDateTime;

/** An availability_slots row joined with its provider and service names. */
public record SlotView(
        long id,
        long providerId,
        String providerName,
        long serviceId,
        String serviceName,
        int durationMinutes,
        int priceCents,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String status) {
}
