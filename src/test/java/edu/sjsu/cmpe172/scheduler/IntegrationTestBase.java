package edu.sjsu.cmpe172.scheduler;

import edu.sjsu.cmpe172.scheduler.model.AppUser;
import edu.sjsu.cmpe172.scheduler.repository.UserRepository;
import edu.sjsu.cmpe172.scheduler.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import javax.sql.DataSource;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

/**
 * Shared setup for tests that run the whole app against a real PostgreSQL.
 * Every test starts from exactly the seed data, so tests cannot affect each other.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
abstract class IntegrationTestBase {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcClient jdbc;

    @Autowired
    protected UserRepository users;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void resetDatabase() {
        jdbc.sql("TRUNCATE appointments, availability_slots, services, providers, users RESTART IDENTITY CASCADE")
                .update();
        new ResourceDatabasePopulator(new ClassPathResource("seed.sql")).execute(dataSource);
    }

    protected AppUser account(String username) {
        return users.findByUsername(username).orElseThrow();
    }

    /** Makes a MockMvc request run as this seeded user (as if they had logged in). */
    protected RequestPostProcessor as(String username) {
        return user(new UserPrincipal(account(username)));
    }

    protected long openSlotId(String providerUsername, String serviceName) {
        return jdbc.sql("""
                        SELECT s.id FROM availability_slots s
                          JOIN providers p ON p.id = s.provider_id
                          JOIN users u ON u.id = p.user_id
                          JOIN services sv ON sv.id = s.service_id
                         WHERE u.username = :username AND sv.name = :service AND s.status = 'OPEN'
                         ORDER BY s.start_time LIMIT 1
                        """)
                .param("username", providerUsername)
                .param("service", serviceName)
                .query(Long.class)
                .single();
    }

    protected long serviceIdOfSlot(long slotId) {
        return jdbc.sql("SELECT service_id FROM availability_slots WHERE id = :id")
                .param("id", slotId).query(Long.class).single();
    }
}
