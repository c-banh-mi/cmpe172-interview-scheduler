package edu.sjsu.cmpe172.scheduler.repository;

import edu.sjsu.cmpe172.scheduler.model.AppUser;
import edu.sjsu.cmpe172.scheduler.model.Role;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UserRepository {

    private static final RowMapper<AppUser> MAPPER = (rs, i) -> new AppUser(
            rs.getLong("id"),
            rs.getString("username"),
            rs.getString("password_hash"),
            rs.getString("full_name"),
            Role.valueOf(rs.getString("role")),
            rs.getObject("provider_id", Long.class));

    private final JdbcClient jdbc;

    public UserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** The user's row (role included) plus their provider profile id, if any. Used at login. */
    public Optional<AppUser> findByUsername(String username) {
        return jdbc.sql("""
                        SELECT u.id, u.username, u.password_hash, u.full_name, u.role, p.id AS provider_id
                          FROM users u
                          LEFT JOIN providers p ON p.user_id = u.id
                         WHERE u.username = :username
                        """)
                .param("username", username)
                .query(MAPPER)
                .optional();
    }
}
