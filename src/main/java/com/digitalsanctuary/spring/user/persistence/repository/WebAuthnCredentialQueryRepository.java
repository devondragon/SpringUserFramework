package com.digitalsanctuary.spring.user.persistence.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import com.digitalsanctuary.spring.user.dto.WebAuthnCredentialInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Repository for WebAuthn credential queries and management.
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class WebAuthnCredentialQueryRepository {

	private final JdbcTemplate jdbcTemplate;

	/**
	 * Get all credentials for a user.
	 *
	 * @param userId the user ID
	 * @return list of credential
	 */
	public List<WebAuthnCredentialInfo> findCredentialsByUserId(Long userId) {
		String sql = """
				SELECT c.id, c.label, c.created, c.last_used, c.transports,
				       c.backup_eligible, c.backup_state, c.enabled
				FROM webauthn_user_credential c
				JOIN webauthn_user_entity wue ON c.user_entity_id = wue.id
				WHERE wue.user_account_id = ? AND c.enabled = true
				ORDER BY c.created DESC
				""";

		return jdbcTemplate.query(sql, this::mapCredentialInfo, userId);
	}

	/**
	 * Check if user has any passkeys.
	 *
	 * @param userId the user ID
	 * @return true if user has at least one enabled passkey
	 */
	public boolean hasCredentials(Long userId) {
		String sql = """
				SELECT COUNT(*)
				FROM webauthn_user_credential c
				JOIN webauthn_user_entity wue ON c.user_entity_id = wue.id
				WHERE wue.user_account_id = ? AND c.enabled = true
				""";

		Integer count = jdbcTemplate.queryForObject(sql, Integer.class, userId);
		return count != null && count > 0;
	}

	/**
	 * Count enabled credentials (used for last-credential protection).
	 *
	 * @param userId the user ID
	 * @return count of enabled credentials
	 */
	public long countEnabledCredentials(Long userId) {
		String sql = """
				SELECT COUNT(*)
				FROM webauthn_user_credential c
				JOIN webauthn_user_entity wue ON c.user_entity_id = wue.id
				WHERE wue.user_account_id = ? AND c.enabled = true
				""";

		Long count = jdbcTemplate.queryForObject(sql, Long.class, userId);
		return count != null ? count : 0L;
	}

	/**
	 * Rename a credential.
	 *
	 * @param credentialId the credential ID
	 * @param newLabel the new label
	 * @param userId the user ID
	 * @return number of rows updated (0 if not found or access denied)
	 */
	@Transactional
	public int renameCredential(String credentialId, String newLabel, Long userId) {
		String sql = """
				UPDATE webauthn_user_credential c
				SET c.label = ?
				WHERE c.id = ?
				AND EXISTS (
				    SELECT 1 FROM webauthn_user_entity wue
				    WHERE wue.id = c.user_entity_id
				    AND wue.user_account_id = ?
				)
				""";

		int updated = jdbcTemplate.update(sql, newLabel, credentialId, userId);
		if (updated > 0) {
			log.info("Renamed credential {} to '{}' for user {}", credentialId, newLabel, userId);
		}
		return updated;
	}

	/**
	 * Delete (disable) a credential.
	 *
	 * @param credentialId the credential ID
	 * @param userId the user ID (for security check)
	 * @return number of rows updated (0 if not found or access denied)
	 */
	@Transactional
	public int deleteCredential(String credentialId, Long userId) {
		String sql = """
				UPDATE webauthn_user_credential c
				SET c.enabled = false
				WHERE c.id = ?
				AND EXISTS (
				    SELECT 1 FROM webauthn_user_entity wue
				    WHERE wue.id = c.user_entity_id
				    AND wue.user_account_id = ?
				)
				""";

		int updated = jdbcTemplate.update(sql, credentialId, userId);
		if (updated > 0) {
			log.info("Disabled credential {} for user {}", credentialId, userId);
		}
		return updated;
	}

	/**
	 * Map ResultSet to WebAuthnCredentialInfo.
	 *
	 * @param rs the ResultSet
	 * @param rowNum the row number
	 * @return the WebAuthnCredentialInfo
	 * @throws SQLException if a database access error occurs
	 */
	private WebAuthnCredentialInfo mapCredentialInfo(ResultSet rs, int rowNum) throws SQLException {
		return WebAuthnCredentialInfo.builder().id(rs.getString("id")).label(rs.getString("label"))
				.created(rs.getTimestamp("created").toInstant())
				.lastUsed(rs.getTimestamp("last_used") != null ? rs.getTimestamp("last_used").toInstant() : null)
				.transports(rs.getString("transports")).backupEligible(rs.getBoolean("backup_eligible"))
				.backupState(rs.getBoolean("backup_state")).enabled(rs.getBoolean("enabled")).build();
	}
}
