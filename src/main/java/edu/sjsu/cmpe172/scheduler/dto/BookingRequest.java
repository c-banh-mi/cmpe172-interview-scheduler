package edu.sjsu.cmpe172.scheduler.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Body of POST /api/customer/appointments. serviceId must match the slot's service. */
public record BookingRequest(
        @NotNull @Positive Long slotId,
        @NotNull @Positive Long serviceId,
        @Size(max = 500) String notes) {
}
