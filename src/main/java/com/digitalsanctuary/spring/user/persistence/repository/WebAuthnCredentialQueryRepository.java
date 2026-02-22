package com.digitalsanctuary.spring.user.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import com.digitalsanctuary.spring.user.dto.WebAuthnCredentialInfo;
import com.digitalsanctuary.spring.user.persistence.model.WebAuthnCredential;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Repository for WebAuthn credential queries and management.
 */
@Repository
@ConditionalOnProperty(name = "user.webauthn.enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
@Slf4j
public class WebAuthnCredentialQueryRepository {

	private final WebAuthnCredentialRepository credentialRepository;

	/**
	 * Get all credentials for a user.
	 *
	 * @param userId the user ID
	 * @return list of credential info
	 */
	public List<WebAuthnCredentialInfo> findCredentialsByUserId(Long userId) {
		return credentialRepository.findByUserEntityUserIdOrderByCreatedDesc(userId).stream()
				.map(this::toCredentialInfo).collect(Collectors.toList());
	}

	/**
	 * Check if user has any passkeys.
	 *
	 * @param userId the user ID
	 * @return true if user has at least one passkey
	 */
	public boolean hasCredentials(Long userId) {
		return credentialRepository.countByUserEntityUserId(userId) > 0;
	}

	/**
	 * Count credentials (used for last-credential protection).
	 *
	 * @param userId the user ID
	 * @return count of credentials
	 */
	public long countCredentials(Long userId) {
		return credentialRepository.countByUserEntityUserId(userId);
	}

	/**
	 * Lock all credentials for a user and return the count.
	 *
	 * @param userId the user ID
	 * @return count of locked credential rows
	 */
	public long lockAndCountCredentials(Long userId) {
		return credentialRepository.lockCredentialIdsByUserEntityUserId(userId).size();
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
		Optional<WebAuthnCredential> optional = findCredentialForUser(credentialId, userId);
		if (optional.isEmpty()) {
			return 0;
		}

		WebAuthnCredential credential = optional.get();
		credential.setLabel(newLabel);
		credentialRepository.save(credential);

		log.info("Renamed credential {} to '{}' for user {}", credentialId, newLabel, userId);
		return 1;
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
		Optional<WebAuthnCredential> optional = findCredentialForUser(credentialId, userId);
		if (optional.isEmpty()) {
			return 0;
		}

		credentialRepository.delete(optional.get());

		log.info("Deleted credential {} for user {}", credentialId, userId);
		return 1;
	}

	/**
	 * Find a credential and verify it belongs to the specified user.
	 *
	 * @param credentialId the credential ID (base64url-encoded)
	 * @param userId the expected owner's user ID
	 * @return the credential if found and owned by the user, empty otherwise
	 */
	private Optional<WebAuthnCredential> findCredentialForUser(String credentialId, Long userId) {
		Optional<WebAuthnCredential> optional = credentialRepository.findByIdWithUser(credentialId);
		if (optional.isEmpty()) {
			return Optional.empty();
		}

		WebAuthnCredential credential = optional.get();
		if (credential.getUserEntity() == null || credential.getUserEntity().getUser() == null
				|| !userId.equals(credential.getUserEntity().getUser().getId())) {
			return Optional.empty();
		}

		return Optional.of(credential);
	}

	/**
	 * Convert a JPA entity to WebAuthnCredentialInfo DTO.
	 *
	 * @param entity the JPA credential entity
	 * @return the DTO
	 */
	private WebAuthnCredentialInfo toCredentialInfo(WebAuthnCredential entity) {
		return WebAuthnCredentialInfo.builder().id(entity.getCredentialId()).label(entity.getLabel())
				.created(entity.getCreated()).lastUsed(entity.getLastUsed())
				.transports(entity.getAuthenticatorTransports()).backupEligible(entity.isBackupEligible())
				.backupState(entity.isBackupState()).build();
	}
}
