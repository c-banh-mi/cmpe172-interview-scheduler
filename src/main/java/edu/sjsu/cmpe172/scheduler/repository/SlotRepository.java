package edu.sjsu.cmpe172.scheduler.repository;

import edu.sjsu.cmpe172.scheduler.dto.SlotFilter;
import edu.sjsu.cmpe172.scheduler.model.Slot;
import edu.sjsu.cmpe172.scheduler.model.SlotView;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class SlotRepository {

    private static final RowMapper<SlotView> MAPPER = (rs, i) -> new SlotView(
            rs.getLong("id"),
            rs.getLong("provider_id"),
            rs.getString("provider_name"),
            rs.getLong("service_id"),
            rs.getString("service_name"),
            rs.getInt("duration_minutes"),
            rs.getInt("price_cents"),
            rs.getTimestamp("start_time").toLocalDateTime(),
            rs.getTimestamp("end_time").toLocalDateTime(),
            rs.getString("status"));

    private static final RowMapper<Slot> SLOT_MAPPER = (rs, i) -> new Slot(
            rs.getLong("id"),
            rs.getLong("provider_id"),
            rs.getLong("service_id"),
            rs.getTimestamp("start_time").toLocalDateTime(),
            rs.getTimestamp("end_time").toLocalDateTime(),
            rs.getString("status"),
            rs.getInt("version"));

    private static final String VIEW_SELECT = """
            SELECT s.id, s.provider_id, p.display_name AS provider_name,
                   s.service_id, sv.name AS service_name, sv.duration_minutes, sv.price_cents,
                   s.start_time, s.end_time, s.status
              FROM availability_slots s
              JOIN providers p ON p.id = s.provider_id
              JOIN services sv ON sv.id = s.service_id
            """;

    private final JdbcClient jdbc;

    public SlotRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** One page of OPEN, future slots matching the filter, earliest first. */
    public List<SlotView> findOpen(SlotFilter filter, int limit, int offset) {
        Map<String, Object> params = new HashMap<>();
        String sql = VIEW_SELECT + openSlotWhere(filter, params) + """
                 ORDER BY s.start_time, s.id
                 LIMIT :limit OFFSET :offset
                """;
        params.put("limit", limit);
        params.put("offset", offset);
        return jdbc.sql(sql).params(params).query(MAPPER).list();
    }

    /** Total number of OPEN, future slots matching the filter (for pagination metadata). */
    public long countOpen(SlotFilter filter) {
        Map<String, Object> params = new HashMap<>();
        String sql = "SELECT COUNT(*) FROM availability_slots s\n" + openSlotWhere(filter, params);
        return jdbc.sql(sql).params(params).query(Long.class).single();
    }

    public Optional<Slot> findById(long id) {
        return jdbc.sql("""
                        SELECT id, provider_id, service_id, start_time, end_time, status, version
                          FROM availability_slots
                         WHERE id = :id
                        """)
                .param("id", id)
                .query(SLOT_MAPPER)
                .optional();
    }

    public Optional<SlotView> findViewById(long id) {
        return jdbc.sql(VIEW_SELECT + " WHERE s.id = :id").param("id", id).query(MAPPER).optional();
    }

    /** A provider's slots that have not ended and were not removed (OPEN and BOOKED), earliest first. */
    public List<SlotView> findUpcomingByProvider(long providerId) {
        return jdbc.sql(VIEW_SELECT + """
                         WHERE s.provider_id = :providerId
                           AND s.status <> 'REMOVED'
                           AND s.end_time > CURRENT_TIMESTAMP
                         ORDER BY s.start_time, s.id
                        """)
                .param("providerId", providerId)
                .query(MAPPER)
                .list();
    }

    /**
     * OPTIMISTIC LOCK. Flips OPEN -> BOOKED only if nobody changed the row since we read it
     * (same version). Returns rows updated: 1 = we won, 0 = someone else got there first.
     *
     * Under READ COMMITTED, PostgreSQL makes a concurrent second UPDATE wait for the first
     * transaction's row lock, then re-checks this WHERE clause against the committed row,
     * where the version has already moved on, so it matches 0 rows.
     */
    public int markBooked(long id, int expectedVersion) {
        return jdbc.sql("""
                        UPDATE availability_slots
                           SET status = 'BOOKED', version = version + 1
                         WHERE id = :id AND version = :version AND status = 'OPEN'
                        """)
                .param("id", id)
                .param("version", expectedVersion)
                .update();
    }

    /** BOOKED -> OPEN after a cancellation, so another customer can book the slot. */
    public int reopen(long id) {
        return jdbc.sql("""
                        UPDATE availability_slots
                           SET status = 'OPEN', version = version + 1
                         WHERE id = :id AND status = 'BOOKED'
                        """)
                .param("id", id)
                .update();
    }

    /** OPEN -> REMOVED (soft delete), with the same version check so it cannot overwrite a fresh booking. */
    public int markRemoved(long id, int expectedVersion) {
        return jdbc.sql("""
                        UPDATE availability_slots
                           SET status = 'REMOVED', version = version + 1
                         WHERE id = :id AND version = :version AND status = 'OPEN'
                        """)
                .param("id", id)
                .param("version", expectedVersion)
                .update();
    }

    public long insert(long providerId, long serviceId, LocalDateTime start, LocalDateTime end) {
        return jdbc.sql("""
                        INSERT INTO availability_slots (provider_id, service_id, start_time, end_time)
                        VALUES (:providerId, :serviceId, :start, :end)
                        RETURNING id
                        """)
                .param("providerId", providerId)
                .param("serviceId", serviceId)
                .param("start", start)
                .param("end", end)
                .query(Long.class)
                .single();
    }

    /** True if the provider already has a live slot overlapping [start, end). */
    public boolean overlapsExisting(long providerId, LocalDateTime start, LocalDateTime end) {
        return jdbc.sql("""
                        SELECT EXISTS (
                            SELECT 1 FROM availability_slots
                             WHERE provider_id = :providerId
                               AND status <> 'REMOVED'
                               AND start_time < :end AND end_time > :start)
                        """)
                .param("providerId", providerId)
                .param("start", start)
                .param("end", end)
                .query(Boolean.class)
                .single();
    }

    /**
     * Builds the WHERE clause from fixed SQL fragments; user input only ever goes
     * into named bind parameters, never into the SQL string itself.
     */
    private static String openSlotWhere(SlotFilter filter, Map<String, Object> params) {
        StringBuilder where = new StringBuilder(" WHERE s.status = 'OPEN' AND s.start_time > CURRENT_TIMESTAMP\n");
        if (filter.providerId() != null) {
            where.append("   AND s.provider_id = :providerId\n");
            params.put("providerId", filter.providerId());
        }
        if (filter.serviceId() != null) {
            where.append("   AND s.service_id = :serviceId\n");
            params.put("serviceId", filter.serviceId());
        }
        if (filter.date() != null) {
            where.append("   AND s.start_time >= :dayStart AND s.start_time < :dayEnd\n");
            params.put("dayStart", filter.date().atStartOfDay());
            params.put("dayEnd", filter.date().plusDays(1).atStartOfDay());
        }
        return where.toString();
    }
}
