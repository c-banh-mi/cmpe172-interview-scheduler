package edu.sjsu.cmpe172.scheduler.notification;

/** A pending_notifications row waiting for redelivery. */
public record PendingNotification(long id, NotificationRequest request, int attempts) {

    public static final String PENDING = "PENDING";
    public static final String SENT = "SENT";
    public static final String FAILED = "FAILED";
}
