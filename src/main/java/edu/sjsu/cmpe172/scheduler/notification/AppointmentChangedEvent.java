package edu.sjsu.cmpe172.scheduler.notification;

/**
 * Published inside the booking/cancel transaction. {@link AppointmentNotificationListener}
 * only sees it once that transaction has committed; if it rolls back, the event is dropped.
 */
public record AppointmentChangedEvent(long appointmentId, NotificationType type) {
}
