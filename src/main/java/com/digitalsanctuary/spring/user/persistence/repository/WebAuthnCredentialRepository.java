package com.digitalsanctuary.spring.user.persistence.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import com.digitalsanctuary.spring.user.persistence.model.WebAuthnCredential;
import com.digitalsanctuary.spring.user.persistence.model.WebAuthnUserEntity;

/**
 * Spring Data JPA repository for {@link WebAuthnCredential}.
 */
public interface WebAuthnCredentialRepository extends JpaRepository<WebAuthnCredential, String> {

	/**
	 * Find all credentials for a WebAuthn user entity.
	 *
	 * @param userEntity the WebAuthn user entity
	 * @return list of credentials
	 */
	List<WebAuthnCredential> findByUserEntity(WebAuthnUserEntity userEntity);

	/**
	 * Find all credentials by user entity ID.
	 *
	 * @param userEntityId the user entity ID
	 * @return list of credentials
	 */
	List<WebAuthnCredential> findByUserEntityId(String userEntityId);

	/**
	 * Find all credentials for an application user, ordered by creation date descending.
	 *
	 * @param userId the application user ID
	 * @return list of credentials ordered by created desc
	 */
	List<WebAuthnCredential> findByUserEntityUserIdOrderByCreatedDesc(Long userId);

	/**
	 * Count credentials for an application user.
	 *
	 * @param userId the application user ID
	 * @return count of credentials
	 */
	long countByUserEntityUserId(Long userId);

	/**
	 * Acquire pessimistic write locks on all credentials for an application user.
	 * Used to make last-credential protection checks and deletion atomic.
	 *
	 * @param userId the application user ID
	 * @return locked credential IDs
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT c.credentialId FROM WebAuthnCredential c WHERE c.userEntity.user.id = :userId")
	List<String> lockCredentialIdsByUserEntityUserId(@Param("userId") Long userId);

	/**
	 * Find a credential by ID with its user entity and application user eagerly fetched.
	 * Avoids N+1 queries when verifying ownership.
	 *
	 * @param credentialId the credential ID (base64url-encoded)
	 * @return the credential with user entity and user eagerly loaded
	 */
	@Query("SELECT c FROM WebAuthnCredential c JOIN FETCH c.userEntity ue JOIN FETCH ue.user WHERE c.credentialId = :credentialId")
	Optional<WebAuthnCredential> findByIdWithUser(@Param("credentialId") String credentialId);

	/**
	 * Delete all credentials for a WebAuthn user entity in a single batch operation.
	 *
	 * @param userEntity the WebAuthn user entity whose credentials should be deleted
	 */
	void deleteByUserEntity(WebAuthnUserEntity userEntity);
}
