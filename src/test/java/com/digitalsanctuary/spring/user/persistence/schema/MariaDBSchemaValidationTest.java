package com.digitalsanctuary.spring.user.persistence.schema;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Validates that Hibernate can create the full schema on MariaDB without errors. This specifically catches the InnoDB
 * row-size limit issue described in GitHub issue #286 where VARBINARY(65535) columns caused silent table creation
 * failure.
 */
@Testcontainers
@DisplayName("MariaDB Schema Validation Tests")
class MariaDBSchemaValidationTest extends AbstractSchemaValidationTest {

	@Container
	static final MariaDBContainer<?> MARIADB = new MariaDBContainer<>("mariadb:11.4")
			.withDatabaseName("testdb")
			.withUsername("test")
			.withPassword("test");

	@DynamicPropertySource
	static void configureProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", MARIADB::getJdbcUrl);
		registry.add("spring.datasource.username", MARIADB::getUsername);
		registry.add("spring.datasource.password", MARIADB::getPassword);
		registry.add("spring.datasource.driver-class-name", () -> "org.mariadb.jdbc.Driver");
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
		registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.MariaDBDialect");
		registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.MariaDBDialect");
	}

	@Override
	protected String getDatabaseName() {
		return "MariaDB";
	}

	@Override
	protected String getSchemaName() {
		return "testdb";
	}

	@Override
	protected Set<String> getAllowedBlobTypes() {
		return Set.of("longblob", "mediumblob", "blob");
	}
}
