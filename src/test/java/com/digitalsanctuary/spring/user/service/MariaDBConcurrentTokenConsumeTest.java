package com.digitalsanctuary.spring.user.service;

import org.junit.jupiter.api.DisplayName;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Runs the concurrent single-use token-consume race against a real MariaDB container, proving the conditional DELETE
 * guard prevents token replay under InnoDB's default isolation.
 */
@Testcontainers
@DisplayName("MariaDB Concurrent Token Consume Tests")
class MariaDBConcurrentTokenConsumeTest extends AbstractConcurrentTokenConsumeTest {

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
}
