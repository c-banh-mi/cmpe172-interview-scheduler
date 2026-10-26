package edu.sjsu.cmpe172.scheduler.notification;

/**
 * The one DTO sent across the distribution boundary. Coarse-grained: a single call carries
 * everything the notification service needs (who, what kind, the finished message), so the
 * service never has to call back into the scheduler or its database.
 *
 * @param idempotencyKey same value on every retry of the same notification, e.g.
 *                       "appointment-12-BOOKING_CONFIRMED"; the service stores a key only once
 * @param appointmentId  reference for support/debugging; the service does not look it up
 */
public record NotificationRequest(
        String idempotencyKey,
        String recipient,
        NotificationType type,
        String message,
        long appointmentId) {

    public static String keyFor(long appointmentId, NotificationType type) {
        return "appointment-" + appointmentId + "-" + type;
    }
}
