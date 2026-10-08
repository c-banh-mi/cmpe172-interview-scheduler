package edu.sjsu.cmpe172.scheduler.dto;

import java.time.LocalDateTime;

/** One of the logged-in provider's own slots, including its status (OPEN or BOOKED). */
public record ProviderSlotDto(
        long id,
        long serviceId,
        String serviceName,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String status) {
}
