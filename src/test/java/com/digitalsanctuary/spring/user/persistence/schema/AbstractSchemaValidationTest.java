package com.digitalsanctuary.spring.user.persistence.schema;

import static org.assertj.core.api.Assertions.assertThat;
import com.digitalsanctuary.spring.user.test.app.TestApplication;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Abstract base class for database schema validation tests. Subclasses provide a real database via Testcontainers and
 * configure Spring to connect to it. This test verifies that Hibernate can create the full schema without errors on each
 * target database.
 *
 * <p>
 * The test uses {@code ddl-auto: create} (via Spring Boot properties) and then queries
 * {@code INFORMATION_SCHEMA.TABLES} to verify all expected tables were created. This catches silent DDL failures like
 * the one described in GitHub issue #286.
 * </p>
 */
@SpringBootTest(classes = TestApplication.class)
abstract class AbstractSchemaValidationTest {

	/**
	 * All tables expected to be created by Hibernate from the entity model. Includes entity tables and join tables.
	 */
	private static final Set<String> EXPECTED_TABLES = Set.of(
			// Entity tables
			"user_account", "role", "privilege", "verification_token", "password_reset_token",
			"password_history_entry", "user_entities", "user_credentials",
			// Join tables
			"users_roles", "roles_privileges");

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@DisplayName("should create all expected tables without errors")
	void shouldCreateAllExpectedTables() {
		List<String> tables = jdbcTemplate.queryForList(
				"SELECT LOWER(table_name) FROM INFORMATION_SCHEMA.TABLES WHERE LOWER(table_schema) = LOWER(?)",
				String.class, getSchemaName());

		assertThat(tables)
				.as("All entity and join tables should be created by Hibernate on %s", getDatabaseName())
				.containsAll(EXPECTED_TABLES);
	}

	@Test
	@DisplayName("should create WebAuthn byte[] columns as BLOB-compatible types (not inline VARBINARY)")
	void shouldCreateWebAuthnBlobColumns() {
		List<String> blobColumns = List.of("public_key", "attestation_object", "attestation_client_data_json");

		for (String column : blobColumns) {
			String dataType = jdbcTemplate.queryForObject(
					"SELECT LOWER(data_type) FROM INFORMATION_SCHEMA.COLUMNS "
							+ "WHERE LOWER(table_schema) = LOWER(?) AND LOWER(table_name) = 'user_credentials' "
							+ "AND LOWER(column_name) = ?",
					String.class, getSchemaName(), column);

			assertThat(dataType)
					.as("Column '%s' on %s should be a BLOB-compatible type, not VARBINARY", column, getDatabaseName())
					.isIn(getAllowedBlobTypes());
		}
	}

	/**
	 * Returns the human-readable database name for assertion messages.
	 */
	protected abstract String getDatabaseName();

	/**
	 * Returns the schema name used in INFORMATION_SCHEMA queries. MariaDB/MySQL uses the database name as schema;
	 * PostgreSQL uses 'public' by default.
	 */
	protected abstract String getSchemaName();

	/**
	 * Returns the set of column data types considered acceptable for BLOB columns on this database.
	 */
	protected abstract Set<String> getAllowedBlobTypes();
}
