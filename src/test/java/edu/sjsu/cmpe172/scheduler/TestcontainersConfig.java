package edu.sjsu.cmpe172.scheduler;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Starts a throwaway PostgreSQL in Docker for integration tests.
 * Skipped under the "localdb" profile (see application-localdb.yml).
 */
@TestConfiguration(proxyBeanMethods = false)
@Profile("!localdb")
public class TestcontainersConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>("postgres:16-alpine");
    }
}
