package com.digitalsanctuary.spring.user.persistence.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
