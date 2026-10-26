package edu.sjsu.cmpe172.scheduler.mocknotify;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Stand-in for an external messaging system (email/SMS provider). It lives in this project for
 * convenience but is treated as a separate system: the scheduler reaches it only over HTTP
 * through NotificationGateway, and it never touches the scheduler's database.
 * It logs and stores each message instead of really sending it.
 */
@RestController
@RequestMapping("/mock-notify")
@ConditionalOnProperty(name = "mock-notify.enabled", havingValue = "true", matchIfMissing = true)
public class MockNotificationController {

    private static final Logger log = LoggerFactory.getLogger(MockNotificationController.class);

    private final MockNotificationStore store;

    public MockNotificationController(MockNotificationStore store) {
        this.store = store;
    }

    /** 201 for a new key, 200 DUPLICATE for a key already stored (idempotent), 503 when switched DOWN. */
    @PostMapping("/api/notifications")
    public ResponseEntity<Map<String, Object>> receive(@Valid @RequestBody MockNotificationMessage msg)
            throws InterruptedException {
        store.inFlight().incrementAndGet();
        try {
            return answer(msg, store.countRequest());
        } finally {
            store.inFlight().decrementAndGet();
        }
    }

    private ResponseEntity<Map<String, Object>> answer(MockNotificationMessage msg, int n)
            throws InterruptedException {
        switch (store.mode()) {
            case DOWN -> {
                log.warn("[mock-notify] DOWN: refusing request #{} ({})", n, msg.idempotencyKey());
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(Map.of("status", "UNAVAILABLE", "receivedAt", Instant.now()));
            }
            case SLOW -> {
                log.warn("[mock-notify] SLOW: waiting {} ms before answering request #{}",
                        store.delay().toMillis(), n);
                Thread.sleep(store.delay().toMillis());
            }
            case UP -> { }
        }
        MockNotificationStore.Accepted result = store.accept(msg);
        MockNotificationStore.Stored s = result.stored();
        if (result.duplicate()) {
            log.info("[mock-notify] Duplicate {} ignored (already stored as {})", msg.idempotencyKey(), s.notificationId());
        } else {
            log.info("[mock-notify] Delivered {} to {}: \"{}\"", msg.type(), msg.recipient(), msg.message());
        }
        return ResponseEntity.status(result.duplicate() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(Map.of("notificationId", s.notificationId(),
                        "status", result.duplicate() ? "DUPLICATE" : "ACCEPTED",
                        "receivedAt", s.receivedAt()));
    }

    /** Everything "sent" so far, oldest first. */
    @GetMapping("/api/notifications")
    public List<MockNotificationStore.Stored> list() {
        return store.all();
    }

    /** Demo switch, e.g. PUT /mock-notify/admin/mode?mode=SLOW&delayMs=5000 */
    @PutMapping("/admin/mode")
    public Map<String, Object> setMode(@RequestParam MockNotificationStore.Mode mode,
                                       @RequestParam(required = false) Long delayMs) {
        if (delayMs != null && delayMs < 0) {
            throw new IllegalArgumentException("delayMs must be 0 or more");
        }
        store.setMode(mode, delayMs == null ? null : Duration.ofMillis(delayMs));
        log.warn("[mock-notify] Mode set to {} (delay {} ms)", mode, store.delay().toMillis());
        return Map.of("mode", store.mode(), "delayMs", store.delay().toMillis());
    }

    @GetMapping("/admin/mode")
    public Map<String, Object> mode() {
        return Map.of("mode", store.mode(), "delayMs", store.delay().toMillis(),
                "requests", store.requestCount(), "inFlight", store.inFlight().get());
    }
}
