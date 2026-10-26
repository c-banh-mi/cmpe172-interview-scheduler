package edu.sjsu.cmpe172.scheduler.mocknotify;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The request body the mock service accepts. Deliberately its own class, not the scheduler's
 * NotificationRequest: the two sides share only the JSON shape, as two separate systems would.
 */
public record MockNotificationMessage(
        @NotBlank @Size(max = 100) String idempotencyKey,
        @NotBlank @Size(max = 255) String recipient,
        @NotBlank String type,
        @NotBlank @Size(max = 2000) String message,
        Long appointmentId) {
}
