package edu.sjsu.cmpe172.scheduler.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;

/** Body of POST /api/provider/slots. The end time is start + the service's duration. */
public record CreateSlotRequest(
        @NotNull @Positive Long serviceId,
        @NotNull @Future LocalDateTime startTime) {
}
