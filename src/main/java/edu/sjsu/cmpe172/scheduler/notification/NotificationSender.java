package edu.sjsu.cmpe172.scheduler.notification;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Fault handling around the remote call: retry with exponential backoff, then fall back to
 * the pending_notifications table. Never throws to the caller, because by the time this runs
 * the booking has already committed and must not be undone by a notification problem.
 */
@Service
public class NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(NotificationSender.class);

    private final NotificationGateway gateway;
    private final PendingNotificationRepository pending;
    private final NotificationProperties props;
    private final MeterRegistry metrics;

    public NotificationSender(NotificationGateway gateway, PendingNotificationRepository pending,
                              NotificationProperties props, MeterRegistry metrics) {
        this.gateway = gateway;
        this.pending = pending;
        this.props = props;
        this.metrics = metrics;
    }

    /** Try up to maxAttempts times; if all fail, park the notification for the redelivery job. */
    public void deliver(NotificationRequest req) {
        NotificationException last = null;
        int attempts = 0;
        while (attempts < props.maxAttempts()) {
            attempts++;
            try {
                NotificationReceipt receipt = gateway.send(req);
                log.info("Sent {} to {} (key {}, notification {}, attempt {})",
                        req.type(), req.recipient(), req.idempotencyKey(), receipt.notificationId(), attempts);
                count("sent", req);
                return;
            } catch (NotificationException e) {
                last = e;
                if (!e.retryable() || attempts == props.maxAttempts()) {
                    break;   // a 4xx will never succeed; or we are out of attempts
                }
                long waitMs = props.backoff().toMillis() << (attempts - 1);   // 200 ms, 400 ms, ...
                log.warn("Notification {} attempt {}/{} failed: {}. Retrying in {} ms",
                        req.idempotencyKey(), attempts, props.maxAttempts(), e.getMessage(), waitMs);
                if (!sleep(waitMs)) {
                    break;   // shutting down: park it now instead of waiting
                }
            }
        }
        fallBack(req, last, attempts);
    }

    private void fallBack(NotificationRequest req, NotificationException error, int attempts) {
        if (error != null && !error.retryable()) {
            // Resending the same request cannot succeed; keep it for a person to look at.
            log.error("Notification {} rejected, not retrying: {}", req.idempotencyKey(), error.getMessage());
            pending.park(req, PendingNotification.FAILED, attempts, error.getMessage());
            count("failed", req);
            return;
        }
        String reason = error == null ? "no attempt made" : error.getMessage();
        log.warn("Notification service unavailable after {} attempt(s); parked {} for redelivery ({})",
                attempts, req.idempotencyKey(), reason);
        pending.park(req, PendingNotification.PENDING, attempts, reason);
        count("parked", req);
    }

    void count(String result, NotificationRequest req) {
        metrics.counter("scheduler.notifications", "result", result, "type", req.type().name()).increment();
    }

    private static boolean sleep(long ms) {
        try {
            Thread.sleep(ms);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
