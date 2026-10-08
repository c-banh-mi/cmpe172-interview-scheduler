package edu.sjsu.cmpe172.scheduler.repository;

import edu.sjsu.cmpe172.scheduler.model.Appointment;
import edu.sjsu.cmpe172.scheduler.model.AppointmentView;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class AppointmentRepository {

    private static final RowMapper<Appointment> MAPPER = (rs, i) -> new Appointment(
            rs.getLong("id"),
            rs.getLong("slot_id"),
            rs.getLong("customer_id"),
            rs.getString("status"),
            rs.getTimestamp("start_time").toLocalDateTime());

    private static final RowMapper<AppointmentView> VIEW_MAPPER = (rs, i) -> new AppointmentView(
            rs.getLong("id"),
            rs.getLong("slot_id"),
            rs.getString("service_name"),
            rs.getString("provider_name"),
            rs.getString("customer_name"),
            rs.getTimestamp("start_time").toLocalDateTime(),
            rs.getTimestamp("end_time").toLocalDateTime(),
            rs.getString("status"),
            rs.getString("notes"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            toLocal(rs.getTimestamp("cancelled_at")));

    // A BOOKED appointment whose slot has ended is reported as COMPLETED even before
    // AppointmentStatusJob has written that status to the row.
    private static final String VIEW_SELECT = """
            SELECT a.id, a.slot_id, sv.name AS service_name, p.display_name AS provider_name,
                   c.full_name AS customer_name, s.start_time, s.end_time,
                   CASE WHEN a.status = 'BOOKED' AND s.end_time <= CURRENT_TIMESTAMP
                        THEN 'COMPLETED' ELSE a.status END AS status,
                   a.notes, a.created_at, a.cancelled_at
              FROM appointments a
              JOIN availability_slots s ON s.id = a.slot_id
              JOIN services sv ON sv.id = a.service_id
              JOIN providers p ON p.id = s.provider_id
              JOIN users c ON c.id = a.customer_id
            """;

    // "Upcoming" = still BOOKED and not yet ended. Everything else is history.
    private static final String UPCOMING = " a.status = 'BOOKED' AND s.end_time > CURRENT_TIMESTAMP ";
    private static final String HISTORY = " NOT (" + UPCOMING + ") ";

    private final JdbcClient jdbc;

    public AppointmentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public long insert(long slotId, long serviceId, long customerId, String notes) {
        return jdbc.sql("""
                        INSERT INTO appointments (slot_id, service_id, customer_id, notes)
                        VALUES (:slotId, :serviceId, :customerId, :notes)
                        RETURNING id
                        """)
                .param("slotId", slotId)
                .param("serviceId", serviceId)
                .param("customerId", customerId)
                .param("notes", notes)
                .query(Long.class)
                .single();
    }

    public Optional<Appointment> findById(long id) {
        return jdbc.sql("""
                        SELECT a.id, a.slot_id, a.customer_id, a.status, s.start_time
                          FROM appointments a
                          JOIN availability_slots s ON s.id = a.slot_id
                         WHERE a.id = :id
                        """)
                .param("id", id)
                .query(MAPPER)
                .optional();
    }

    public Optional<AppointmentView> findViewById(long id) {
        return jdbc.sql(VIEW_SELECT + " WHERE a.id = :id").param("id", id).query(VIEW_MAPPER).optional();
    }

    /** BOOKED -> CANCELLED. Returns 0 if it was no longer BOOKED (e.g. cancelled twice at once). */
    public int cancel(long id) {
        return jdbc.sql("""
                        UPDATE appointments
                           SET status = 'CANCELLED', cancelled_at = CURRENT_TIMESTAMP
                         WHERE id = :id AND status = 'BOOKED'
                        """)
                .param("id", id)
                .update();
    }

    /** True if the customer already holds a BOOKED appointment overlapping [start, end). */
    public boolean customerHasOverlap(long customerId, LocalDateTime start, LocalDateTime end) {
        return jdbc.sql("""
                        SELECT EXISTS (
                            SELECT 1 FROM appointments a
                              JOIN availability_slots s ON s.id = a.slot_id
                             WHERE a.customer_id = :customerId
                               AND a.status = 'BOOKED'
                               AND s.start_time < :end AND s.end_time > :start)
                        """)
                .param("customerId", customerId)
                .param("start", start)
                .param("end", end)
                .query(Boolean.class)
                .single();
    }

    public List<AppointmentView> findForCustomer(long customerId, boolean upcoming) {
        return jdbc.sql(VIEW_SELECT + " WHERE a.customer_id = :id AND" + (upcoming ? UPCOMING : HISTORY)
                        + orderBy(upcoming))
                .param("id", customerId)
                .query(VIEW_MAPPER)
                .list();
    }

    public List<AppointmentView> findForProvider(long providerId, boolean upcoming) {
        return jdbc.sql(VIEW_SELECT + " WHERE s.provider_id = :id AND" + (upcoming ? UPCOMING : HISTORY)
                        + orderBy(upcoming))
                .param("id", providerId)
                .query(VIEW_MAPPER)
                .list();
    }

    /** Persists COMPLETED for BOOKED appointments whose slot has ended. Returns rows changed. */
    public int markPastAsCompleted() {
        return jdbc.sql("""
                        UPDATE appointments a
                           SET status = 'COMPLETED'
                          FROM availability_slots s
                         WHERE s.id = a.slot_id
                           AND a.status = 'BOOKED'
                           AND s.end_time <= CURRENT_TIMESTAMP
                        """)
                .update();
    }

    // Upcoming: soonest first. History: most recent first. Capped; no paging needed at this size.
    private static String orderBy(boolean upcoming) {
        return upcoming ? " ORDER BY s.start_time, a.id LIMIT 200" : " ORDER BY s.start_time DESC, a.id DESC LIMIT 200";
    }

    private static LocalDateTime toLocal(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }
}
