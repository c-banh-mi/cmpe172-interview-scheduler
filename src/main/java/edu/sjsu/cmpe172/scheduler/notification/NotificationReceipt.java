package edu.sjsu.cmpe172.scheduler.notification;

import java.time.Instant;

/** The notification service's answer. status is ACCEPTED, or DUPLICATE for a key it already has. */
public record NotificationReceipt(String notificationId, String status, Instant receivedAt) {
}
