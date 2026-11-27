package com.edusync.edusync.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Starts the whole backend once: every bean, security, Flyway V1..Vn on an
 * empty database, and Hibernate's check that the entities match the tables.
 * Fails if any of that is broken. Needs Docker running (see TestcontainersConfiguration).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class EdusyncBackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
