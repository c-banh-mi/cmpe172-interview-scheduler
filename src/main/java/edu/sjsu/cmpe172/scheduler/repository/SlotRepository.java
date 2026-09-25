package edu.sjsu.cmpe172.scheduler.repository;

import edu.sjsu.cmpe172.scheduler.dto.SlotFilter;
import edu.sjsu.cmpe172.scheduler.model.SlotView;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    private final JdbcClient jdbc;

    public SlotRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** One page of OPEN, future slots matching the filter, earliest first. */
    public List<SlotView> findOpen(SlotFilter filter, int limit, int offset) {
        Map<String, Object> params = new HashMap<>();
        String sql = """
                SELECT s.id, s.provider_id, p.display_name AS provider_name,
                       s.service_id, sv.name AS service_name, sv.duration_minutes, sv.price_cents,
                       s.start_time, s.end_time, s.status
                  FROM availability_slots s
                  JOIN providers p ON p.id = s.provider_id
                  JOIN services sv ON sv.id = s.service_id
                """ + openSlotWhere(filter, params) + """
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
