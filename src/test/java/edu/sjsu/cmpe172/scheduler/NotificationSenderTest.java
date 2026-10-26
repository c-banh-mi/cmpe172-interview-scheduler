package edu.sjsu.cmpe172.scheduler;

import edu.sjsu.cmpe172.scheduler.notification.NotificationException;
import edu.sjsu.cmpe172.scheduler.notification.NotificationGateway;
import edu.sjsu.cmpe172.scheduler.notification.NotificationProperties;
import edu.sjsu.cmpe172.scheduler.notification.NotificationReceipt;
import edu.sjsu.cmpe172.scheduler.notification.NotificationRequest;
import edu.sjsu.cmpe172.scheduler.notification.NotificationSender;
import edu.sjsu.cmpe172.scheduler.notification.NotificationType;
import edu.sjsu.cmpe172.scheduler.notification.PendingNotification;
import edu.sjsu.cmpe172.scheduler.notification.PendingNotificationRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Retry and fallback rules of NotificationSender, with a fake gateway. */
class NotificationSenderTest {

    private static final NotificationRequest REQ = new NotificationRequest(
            "appointment-7-BOOKING_CONFIRMED", "sam@example.com", NotificationType.BOOKING_CONFIRMED,
            "Hi Sam, your session is confirmed.", 7);
    private static final NotificationReceipt OK = new NotificationReceipt("n-1", "ACCEPTED", Instant.now());

    private final NotificationGateway gateway = mock(NotificationGateway.class);
    private final PendingNotificationRepository pending = mock(PendingNotificationRepository.class);
    private final SimpleMeterRegistry metrics = new SimpleMeterRegistry();
    private final NotificationSender sender = new NotificationSender(gateway, pending,
            new NotificationProperties(true, Duration.ofSeconds(1), Duration.ofSeconds(1), 3, Duration.ofMillis(1), 10),
            metrics);

    private static NotificationException down() {
        return new NotificationException("Connection refused", true, null);
    }

    private double count(String result) {
        return metrics.counter("scheduler.notifications", "result", result, "type", "BOOKING_CONFIRMED").count();
    }

    @Test
    void firstTrySucceeds() {
        when(gateway.send(REQ)).thenReturn(OK);

        sender.deliver(REQ);

        verify(gateway, times(1)).send(REQ);
        verify(pending, never()).park(any(), anyString(), anyInt(), any());
        assertThat(count("sent")).isEqualTo(1);
    }

    @Test
    void retriesTransientFailureThenSucceeds() {
        when(gateway.send(REQ)).thenThrow(down()).thenThrow(down()).thenReturn(OK);

        sender.deliver(REQ);

        verify(gateway, times(3)).send(REQ);
        verify(pending, never()).park(any(), anyString(), anyInt(), any());
    }

    @Test
    void serviceDownAfterAllAttemptsIsParkedForRedelivery() {
        when(gateway.send(REQ)).thenThrow(down());

        sender.deliver(REQ);

        verify(gateway, times(3)).send(REQ);
        verify(pending).park(eq(REQ), eq(PendingNotification.PENDING), eq(3), anyString());
        assertThat(count("parked")).isEqualTo(1);
    }

    @Test
    void rejectedRequestIsNotRetried() {
        when(gateway.send(REQ)).thenThrow(new NotificationException("400 BAD_REQUEST", false, null));

        sender.deliver(REQ);

        verify(gateway, times(1)).send(REQ);
        verify(pending).park(eq(REQ), eq(PendingNotification.FAILED), eq(1), anyString());
        assertThat(count("failed")).isEqualTo(1);
    }
}
