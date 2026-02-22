package com.digitalsanctuary.spring.user.service;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.digitalsanctuary.spring.user.dto.WebAuthnCredentialInfo;
import com.digitalsanctuary.spring.user.exceptions.WebAuthnException;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.repository.WebAuthnCredentialQueryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for managing WebAuthn credentials.
 *
 * <p>
 * Handles credential listing, renaming, and deletion. It includes important safety features like last-credential 
 * protection to prevent users from being locked out of their accounts.
 * </p>
 *
 * <p>
 * <b>Last-Credential Protection:</b> The service prevents deletion of the last passkey if the user has no password, 
 * ensuring users always have a way to authenticate.
 * </p>
 *
 * @see WebAuthnCredentialQueryRepository
 * @see WebAuthnException
 */
@Service
@ConditionalOnProperty(name = "user.webauthn.enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
@Slf4j
public class WebAuthnCredentialManagementService {

	private final WebAuthnCredentialQueryRepository credentialQueryRepository;

	/**
	 * Get all credentials for a user.
	 *
	 * @param user the user to get credentials for
	 * @return list of credential information.
	 */
	public List<WebAuthnCredentialInfo> getUserCredentials(User user) {
		return credentialQueryRepository.findCredentialsByUserId(user.getId());
	}

	/**
	 * Check if user has any passkeys.
	 *
	 * @param user the user to check
	 * @return true if user has at least one enabled passkey, false otherwise
	 */
	public boolean hasCredentials(User user) {
		return credentialQueryRepository.hasCredentials(user.getId());
	}

	/**
	 * Rename a credential label.
	 *
	 * <p>
	 * Help users identify their passkeys (e.g., "My iPhone", "Work Laptop"). The label must be non-empty and no more than 64 characters.
	 * </p>
	 *
	 * @param credentialId the credential ID to rename
	 * @param newLabel the new label
	 * @param user the user performing the operation
	 * @throws WebAuthnException if the credential is not found, access is denied, or the label is invalid
	 */
	@Transactional
	public void renameCredential(String credentialId, String newLabel, User user) {
		validateLabel(newLabel);

		int updated = credentialQueryRepository.renameCredential(credentialId, newLabel.trim(), user.getId());

		if (updated == 0) {
			throw new WebAuthnException("Credential not found or access denied");
		}

		log.info("User {} renamed credential {}", user.getEmail(), credentialId);
	}

	/**
	 * Delete a credential with last-credential protection.
	 *
	 * <p>
	 * Deletes a credential. This operation includes important safety logic:
	 * </p>
	 * <ul>
	 * <li>If this is the user's last passkey AND the user has no password, deletion is blocked</li>
	 * <li>This prevents users from being locked out of their accounts</li>
	 * <li>Users must either add a password or register another passkey before deleting their last one</li>
	 * </ul>
	 *
	 * <p>
	 * <b>Security:</b> This method verifies that the credential belongs to the specified user before allowing deletion.
	 * </p>
	 *
	 * @param credentialId the credential ID to delete
	 * @param user the user performing the operation
	 * @throws WebAuthnException if the credential is not found, access is denied, or deletion would lock out the user
	 */
	@Transactional
	public void deleteCredential(String credentialId, User user) {
		// Lock all user credentials before checking count and deleting to avoid TOCTOU races.
		long enabledCount = credentialQueryRepository.lockAndCountCredentials(user.getId());

		if (enabledCount == 1 && (user.getPassword() == null || user.getPassword().isEmpty())) {
			throw new WebAuthnException(
					"Cannot delete last passkey. User would be locked out. " + "Please add a password or another passkey first.");
		}

		int updated = credentialQueryRepository.deleteCredential(credentialId, user.getId());

		if (updated == 0) {
			throw new WebAuthnException("Credential not found or access denied");
		}

		log.info("User {} deleted credential {}", user.getEmail(), credentialId);
	}

	/**
	 * Validate credential label.
	 *
	 * <p>
	 * Ensures the label meets the following requirements:
	 * </p>
	 * <ul>
	 * <li>Not null or empty (after trimming)</li>
	 * <li>No more than 64 characters</li>
	 * </ul>
	 *
	 * @param label the label to validate
	 * @throws WebAuthnException if the label is invalid
	 */
	private void validateLabel(String label) {
		if (label == null || label.trim().isEmpty()) {
			throw new WebAuthnException("Credential label cannot be empty");
		}
		if (label.trim().length() > 64) {
			throw new WebAuthnException("Credential label too long (max 64 characters)");
		}
	}
}
