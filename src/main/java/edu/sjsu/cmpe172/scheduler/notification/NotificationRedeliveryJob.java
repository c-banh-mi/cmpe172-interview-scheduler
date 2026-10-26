package edu.sjsu.cmpe172.scheduler.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Recovery: re-sends parked notifications once the notification service is back. One attempt
 * per row per run. Re-sending is safe because each row keeps its idempotency key, so if an
 * earlier attempt did reach the service (only the answer timed out), the service reports
 * DUPLICATE instead of notifying the customer twice.
 */
@Component
public class NotificationRedeliveryJob {

    private static final Logger log = LoggerFactory.getLogger(NotificationRedeliveryJob.class);
    private static final int BATCH = 50;

    private final PendingNotificationRepository pending;
    private final NotificationGateway gateway;
    private final NotificationSender sender;
    private final NotificationProperties props;

    public NotificationRedeliveryJob(PendingNotificationRepository pending, NotificationGateway gateway,
                                     NotificationSender sender, NotificationProperties props) {
        this.pending = pending;
        this.gateway = gateway;
        this.sender = sender;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "${notification.redelivery-interval:PT30S}",
            initialDelayString = "${notification.redelivery-initial-delay:PT15S}")
    public void redeliver() {
        if (!props.enabled()) {
            return;
        }
        for (PendingNotification p : pending.findPending(BATCH)) {
            NotificationRequest req = p.request();
            try {
                NotificationReceipt receipt = gateway.send(req);
                pending.markSent(p.id());
                sender.count("redelivered", req);
                log.info("Redelivered {} to {} after {} failed attempt(s) (service said {})",
                        req.idempotencyKey(), req.recipient(), p.attempts(), receipt.status());
            } catch (NotificationException e) {
                boolean giveUp = !e.retryable() || p.attempts() + 1 >= props.maxRedeliveries();
                pending.recordFailure(p.id(), giveUp ? PendingNotification.FAILED : PendingNotification.PENDING,
                        e.getMessage());
                if (giveUp) {
                    sender.count("failed", req);
                    log.error("Giving up on notification {} after {} attempts: {}",
                            req.idempotencyKey(), p.attempts() + 1, e.getMessage());
                } else {
                    log.warn("Redelivery of {} failed (attempt {}): {}",
                            req.idempotencyKey(), p.attempts() + 1, e.getMessage());
                    // The service is still down; leave the rest for the next run.
                    return;
                }
            }
        }
    }
}
