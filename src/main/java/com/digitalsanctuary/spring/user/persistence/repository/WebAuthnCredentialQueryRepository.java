package com.digitalsanctuary.spring.user.persistence.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
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
				SELECT c.credential_id, c.label, c.created, c.last_used,
				       c.authenticator_transports, c.backup_eligible, c.backup_state
				FROM user_credentials c
				JOIN user_entities ue ON c.user_entity_user_id = ue.id
				WHERE ue.user_account_id = ?
				ORDER BY c.created DESC
				""";

		return jdbcTemplate.query(sql, this::mapCredentialInfo, userId);
	}

	/**
	 * Check if user has any passkeys.
	 *
	 * @param userId the user ID
	 * @return true if user has at least one passkey
	 */
	public boolean hasCredentials(Long userId) {
		String sql = """
				SELECT COUNT(*)
				FROM user_credentials c
				JOIN user_entities ue ON c.user_entity_user_id = ue.id
				WHERE ue.user_account_id = ?
				""";

		Integer count = jdbcTemplate.queryForObject(sql, Integer.class, userId);
		return count != null && count > 0;
	}

	/**
	 * Count credentials (used for last-credential protection).
	 *
	 * @param userId the user ID
	 * @return count of credentials
	 */
	public long countCredentials(Long userId) {
		String sql = """
				SELECT COUNT(*)
				FROM user_credentials c
				JOIN user_entities ue ON c.user_entity_user_id = ue.id
				WHERE ue.user_account_id = ?
				""";

		Long count = jdbcTemplate.queryForObject(sql, Long.class, userId);
		return count != null ? count : 0L;
	}

	/**
	 * Rename a credential.
	 *
	 * @param credentialId the credential ID (base64url-encoded)
	 * @param newLabel the new label
	 * @param userId the user ID
	 * @return number of rows updated (0 if not found or access denied)
	 */
	@Transactional
	public int renameCredential(String credentialId, String newLabel, Long userId) {
		byte[] credIdBytes = Base64.getUrlDecoder().decode(credentialId);

		String sql = """
				UPDATE user_credentials c
				SET c.label = ?
				WHERE c.credential_id = ?
				AND EXISTS (
				    SELECT 1 FROM user_entities ue
				    WHERE ue.id = c.user_entity_user_id
				    AND ue.user_account_id = ?
				)
				""";

		int updated = jdbcTemplate.update(sql, newLabel, credIdBytes, userId);
		if (updated > 0) {
			log.info("Renamed credential {} to '{}' for user {}", credentialId, newLabel, userId);
		}
		return updated;
	}

	/**
	 * Delete a credential.
	 *
	 * @param credentialId the credential ID (base64url-encoded)
	 * @param userId the user ID (for security check)
	 * @return number of rows deleted (0 if not found or access denied)
	 */
	@Transactional
	public int deleteCredential(String credentialId, Long userId) {
		byte[] credIdBytes = Base64.getUrlDecoder().decode(credentialId);

		String sql = """
				DELETE FROM user_credentials
				WHERE credential_id = ?
				AND EXISTS (
				    SELECT 1 FROM user_entities ue
				    WHERE ue.id = user_entity_user_id
				    AND ue.user_account_id = ?
				)
				""";

		int deleted = jdbcTemplate.update(sql, credIdBytes, userId);
		if (deleted > 0) {
			log.info("Deleted credential {} for user {}", credentialId, userId);
		}
		return deleted;
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
		byte[] credId = rs.getBytes("credential_id");
		String credIdStr = Base64.getUrlEncoder().withoutPadding().encodeToString(credId);

		return WebAuthnCredentialInfo.builder().id(credIdStr).label(rs.getString("label"))
				.created(rs.getTimestamp("created").toInstant())
				.lastUsed(rs.getTimestamp("last_used") != null ? rs.getTimestamp("last_used").toInstant() : null)
				.transports(rs.getString("authenticator_transports")).backupEligible(rs.getBoolean("backup_eligible"))
				.backupState(rs.getBoolean("backup_state")).build();
	}
}
