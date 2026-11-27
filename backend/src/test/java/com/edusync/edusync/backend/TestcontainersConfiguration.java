package com.edusync.edusync.backend;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Gives every Spring test its own empty PostgreSQL 18 + pgvector in Docker
 * (the same image as our dev database). Flyway runs V1, V2... on it at startup,
 * so a test run also proves every migration works on an empty database.
 * The container is removed when the tests finish. Needs Docker running.
 *
 * Use it with: @Import(TestcontainersConfiguration.class)
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection // points spring.datasource.* at this container, overriding application.properties
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(
                DockerImageName.parse("pgvector/pgvector:pg18").asCompatibleSubstituteFor("postgres"));
    }
}
