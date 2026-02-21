package com.digitalsanctuary.spring.user.persistence.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.model.WebAuthnUserEntity;

/**
 * Spring Data JPA repository for {@link WebAuthnUserEntity}.
 */
public interface WebAuthnUserEntityRepository extends JpaRepository<WebAuthnUserEntity, String> {

	/**
	 * Find by username (email).
	 *
	 * @param name the username
	 * @return the entity if found
	 */
	Optional<WebAuthnUserEntity> findByName(String name);

	/**
	 * Find by application user.
	 *
	 * @param user the application user
	 * @return the entity if found
	 */
	Optional<WebAuthnUserEntity> findByUser(User user);

	/**
	 * Find by application user ID.
	 *
	 * @param userId the user ID
	 * @return the entity if found
	 */
	Optional<WebAuthnUserEntity> findByUserId(Long userId);
}
