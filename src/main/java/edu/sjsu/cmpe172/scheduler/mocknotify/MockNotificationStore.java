package edu.sjsu.cmpe172.scheduler.mocknotify;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory "outbox" of the mock service, plus a switch that makes it misbehave for demos and
 * tests. Keyed by idempotency key: the same key is stored only once, however often it is sent.
 */
@Component
public class MockNotificationStore {

    /** UP answers normally, SLOW waits {@code delay} first, DOWN answers 503. */
    public enum Mode { UP, SLOW, DOWN }

    public record Stored(String notificationId, MockNotificationMessage message, Instant receivedAt) {
    }

    /** Result of {@link #accept}: duplicate is true if this key had already been stored. */
    public record Accepted(Stored stored, boolean duplicate) {
    }

    private final Map<String, Stored> byKey = new ConcurrentHashMap<>();
    private final AtomicInteger requestCount = new AtomicInteger();
    private final AtomicInteger inFlight = new AtomicInteger();
    private volatile Mode mode = Mode.UP;
    private volatile Duration delay = Duration.ofSeconds(5);

    public Accepted accept(MockNotificationMessage msg) {
        boolean[] isNew = {false};
        Stored stored = byKey.computeIfAbsent(msg.idempotencyKey(), k -> {
            isNew[0] = true;
            return new Stored(UUID.randomUUID().toString(), msg, Instant.now());
        });
        return new Accepted(stored, !isNew[0]);
    }

    public List<Stored> all() {
        List<Stored> list = new ArrayList<>(byKey.values());
        list.sort(Comparator.comparing(Stored::receivedAt));
        return list;
    }

    /** Every POST that reached the service, including duplicates and ones answered with 503. */
    public int countRequest() {
        return requestCount.incrementAndGet();
    }

    public int requestCount() {
        return requestCount.get();
    }

    /** Requests still being answered (e.g. sleeping in SLOW mode). */
    public AtomicInteger inFlight() {
        return inFlight;
    }

    public void clear() {
        byKey.clear();
        requestCount.set(0);
        mode = Mode.UP;
    }

    public Mode mode() {
        return mode;
    }

    public Duration delay() {
        return delay;
    }

    public void setMode(Mode mode, Duration delay) {
        this.mode = mode;
        if (delay != null) {
            this.delay = delay;
        }
    }
}
