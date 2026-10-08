package edu.sjsu.cmpe172.scheduler.dto;

import java.time.LocalDateTime;

public record AppointmentDto(
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
