package edu.sjsu.cmpe172.scheduler.repository;

import edu.sjsu.cmpe172.scheduler.model.ServiceOffering;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ServiceOfferingRepository {

    private static final RowMapper<ServiceOffering> MAPPER = (rs, i) -> new ServiceOffering(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("description"),
            rs.getInt("duration_minutes"),
            rs.getInt("price_cents"));

    private final JdbcClient jdbc;

    public ServiceOfferingRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<ServiceOffering> findAll() {
        return jdbc.sql("""
                        SELECT id, name, description, duration_minutes, price_cents
                          FROM services
                         ORDER BY name
                        """)
                .query(MAPPER)
                .list();
    }
}
