package edu.sjsu.cmpe172.scheduler.notification;

/**
 * The scheduler's only view of the external notification system (Gateway pattern).
 * Callers depend on this interface, not on HTTP, so the mock can be swapped for a real
 * channel (Discord/Slack webhook, SMTP) without touching booking code.
 */
public interface NotificationGateway {

    /**
     * Delivers one notification with one remote call.
     *
     * @throws NotificationException if the call failed or timed out
     */
    NotificationReceipt send(NotificationRequest request);
}
