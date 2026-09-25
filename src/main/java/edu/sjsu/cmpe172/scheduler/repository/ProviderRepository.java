package edu.sjsu.cmpe172.scheduler.repository;

import edu.sjsu.cmpe172.scheduler.model.Provider;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ProviderRepository {

    private static final RowMapper<Provider> MAPPER = (rs, i) -> new Provider(
            rs.getLong("id"),
            rs.getLong("user_id"),
            rs.getString("display_name"),
            rs.getString("headline"),
            rs.getString("bio"));

    private final JdbcClient jdbc;

    public ProviderRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Provider> findAll() {
        return jdbc.sql("""
                        SELECT id, user_id, display_name, headline, bio
                          FROM providers
                         ORDER BY display_name
                        """)
                .query(MAPPER)
                .list();
    }
}
