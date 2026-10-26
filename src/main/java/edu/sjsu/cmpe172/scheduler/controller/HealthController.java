package edu.sjsu.cmpe172.scheduler.controller;

import edu.sjsu.cmpe172.scheduler.notification.PendingNotification;
import edu.sjsu.cmpe172.scheduler.notification.PendingNotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GET /health: 200 {"status":"UP", ...} when the database answers, 503 {"status":"DOWN", ...}
 * when it does not (a load balancer or Docker would stop sending traffic / restart the app).
 * Notification problems show as "DEGRADED" but keep the app UP, because bookings still work
 * without the notification service.
 */
@RestController
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    private final JdbcClient jdbc;
    private final PendingNotificationRepository pending;

    public HealthController(JdbcClient jdbc, PendingNotificationRepository pending) {
        this.jdbc = jdbc;
        this.pending = pending;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> database = new LinkedHashMap<>();
        Map<String, Object> notifications = new LinkedHashMap<>();
        boolean dbUp;

        long start = System.nanoTime();
        try {
            jdbc.sql("SELECT 1").query(Integer.class).single();
            dbUp = true;
            database.put("status", "UP");
            database.put("responseTimeMs", (System.nanoTime() - start) / 1_000_000);

            int waiting = pending.countByStatus(PendingNotification.PENDING);
            int failed = pending.countByStatus(PendingNotification.FAILED);
            notifications.put("status", waiting + failed == 0 ? "UP" : "DEGRADED");
            notifications.put("pending", waiting);
            notifications.put("failed", failed);
        } catch (DataAccessException e) {
            dbUp = false;
            log.error("Health check: database unreachable: {}", e.getMostSpecificCause().getMessage());
            database.put("status", "DOWN");
            database.put("error", e.getMostSpecificCause().getClass().getSimpleName());
            notifications.put("status", "UNKNOWN");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", dbUp ? "UP" : "DOWN");
        body.put("timestamp", Instant.now());
        body.put("database", database);
        body.put("notifications", notifications);
        return ResponseEntity.status(dbUp ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }
}
