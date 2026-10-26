package edu.sjsu.cmpe172.scheduler.notification;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

/** The fallback store: notifications the service did not accept, kept for redelivery. */
@Repository
public class PendingNotificationRepository {

    private static final RowMapper<PendingNotification> MAPPER = (rs, i) -> new PendingNotification(
            rs.getLong("id"),
            new NotificationRequest(
                    rs.getString("idempotency_key"),
                    rs.getString("recipient"),
                    NotificationType.valueOf(rs.getString("type")),
                    rs.getString("message"),
                    rs.getLong("appointment_id")),
            rs.getInt("attempts"));

    private final JdbcClient jdbc;

    public PendingNotificationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Store a failed notification. The unique idempotency_key makes parking it twice a no-op. */
    public void park(NotificationRequest req, String status, int attempts, String error) {
        jdbc.sql("""
                        INSERT INTO pending_notifications
                               (idempotency_key, appointment_id, recipient, type, message, status, attempts, last_error)
                        VALUES (:key, :appointmentId, :recipient, :type, :message, :status, :attempts, :error)
                        ON CONFLICT (idempotency_key) DO NOTHING
                        """)
                .param("key", req.idempotencyKey())
                .param("appointmentId", req.appointmentId())
                .param("recipient", req.recipient())
                .param("type", req.type().name())
                .param("message", req.message())
                .param("status", status)
                .param("attempts", attempts)
                .param("error", truncate(error))
                .update();
    }

    /** Oldest PENDING rows first. */
    public List<PendingNotification> findPending(int limit) {
        return jdbc.sql("""
                        SELECT id, idempotency_key, appointment_id, recipient, type, message, attempts
                          FROM pending_notifications
                         WHERE status = 'PENDING'
                         ORDER BY created_at, id
                         LIMIT :limit
                        """)
                .param("limit", limit)
                .query(MAPPER)
                .list();
    }

    public void markSent(long id) {
        jdbc.sql("""
                        UPDATE pending_notifications
                           SET status = 'SENT', attempts = attempts + 1, last_error = NULL, updated_at = CURRENT_TIMESTAMP
                         WHERE id = :id
                        """)
                .param("id", id)
                .update();
    }

    /** One more failed attempt; status becomes FAILED (no more retries) or stays PENDING. */
    public void recordFailure(long id, String status, String error) {
        jdbc.sql("""
                        UPDATE pending_notifications
                           SET status = :status, attempts = attempts + 1, last_error = :error, updated_at = CURRENT_TIMESTAMP
                         WHERE id = :id
                        """)
                .param("id", id)
                .param("status", status)
                .param("error", truncate(error))
                .update();
    }

    public int countByStatus(String status) {
        return jdbc.sql("SELECT COUNT(*) FROM pending_notifications WHERE status = :status")
                .param("status", status)
                .query(Integer.class)
                .single();
    }

    private static String truncate(String s) {
        return s == null || s.length() <= 500 ? s : s.substring(0, 500);
    }
}
