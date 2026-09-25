package edu.sjsu.cmpe172.scheduler.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SlotDto(
        long id,
        long providerId,
        String providerName,
        long serviceId,
        String serviceName,
        int durationMinutes,
        BigDecimal price,
        LocalDateTime startTime,
        LocalDateTime endTime) {
}
