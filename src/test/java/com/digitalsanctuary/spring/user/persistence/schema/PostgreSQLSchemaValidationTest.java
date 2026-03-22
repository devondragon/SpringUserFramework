package com.digitalsanctuary.spring.user.persistence.schema;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Validates that Hibernate can create the full schema on PostgreSQL without errors. Ensures the byte[] columns map to
 * {@code bytea} (not {@code oid}), which would happen if {@code @Lob} were used instead of
 * {@code length = Length.LONG32}.
 */
@Testcontainers
@DisplayName("PostgreSQL Schema Validation Tests")
class PostgreSQLSchemaValidationTest extends AbstractSchemaValidationTest {

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17")
			.withDatabaseName("testdb")
			.withUsername("test")
			.withPassword("test");

	@DynamicPropertySource
	static void configureProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
		registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
		registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
	}

	@Override
	protected String getDatabaseName() {
		return "PostgreSQL";
	}

	@Override
	protected String getSchemaName() {
		return "public";
	}

	@Override
	protected Set<String> getAllowedBlobTypes() {
		return Set.of("bytea");
	}
}
