package com.digitalsanctuary.spring.user.persistence.repository;

import java.nio.ByteBuffer;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import com.digitalsanctuary.spring.user.persistence.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 * This repository bridges the gap between Spring Security's WebAuthn system and the Spring User Framework's User entity. It handles edge cases like
 * anonymousUser and null usernames, and automatically creates WebAuthn user entities for existing users.
 * </p>
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class WebAuthnUserEntityBridge {

	private final JdbcTemplate jdbcTemplate;
	private final UserRepository userRepository;
	private final PublicKeyCredentialUserEntityRepository baseRepository;

	/**
	 *
	 * @param username the username (email) to look up
	 * @return Optional containing the PublicKeyCredentialUserEntity, or empty if not found
	 */
	public Optional<PublicKeyCredentialUserEntity> findByUsername(String username) {
		// Handle edge cases that can occur during login
		if (username == null || username.isEmpty() || "anonymousUser".equals(username)) {
			log.debug("Ignoring invalid username: {}", username);
			return Optional.empty();
		}

		// Check if user entity already exists
		Optional<PublicKeyCredentialUserEntity> existing = baseRepository.findByUsername(username);
		if (existing.isPresent()) {
			return existing;
		}

		// User entity doesn't exist yet - check if application user exists
		User user = userRepository.findByEmail(username);
		if (user == null) {
			log.debug("No application user found for username: {}", username);
			return Optional.empty();
		}

		// Create WebAuthn user entity for this application user
		PublicKeyCredentialUserEntity entity = createUserEntity(user);
		baseRepository.save(entity);

		return Optional.of(entity);
	}

	/**
	 * Create user entity from User model with user_account_id.
	 *
	 * @param user the User entity
	 * @return the created PublicKeyCredentialUserEntity
	 */
	@Transactional
	public PublicKeyCredentialUserEntity createUserEntity(User user) {
		byte[] userId = longToBytes(user.getId());
		String displayName = user.getFullName();

		PublicKeyCredentialUserEntity entity = ImmutablePublicKeyCredentialUserEntity.builder().name(user.getEmail()).id(userId)
				.displayName(displayName).build();

		// Save with user_account_id
		String insertSql = """
				INSERT INTO webauthn_user_entity
				(name, user_id, display_name, user_account_id)
				VALUES (?, ?, ?, ?)
				ON DUPLICATE KEY UPDATE display_name = VALUES(display_name)
				""";

		jdbcTemplate.update(insertSql, entity.getName(), entity.getId(), entity.getDisplayName(), user.getId());

		log.info("Created WebAuthn user entity for user: {}", user.getEmail());
		return entity;
	}

	/**
	 * Convert Long ID to byte array for WebAuthn user ID.
	 *
	 * @param value the Long value to convert
	 * @return byte array representation of the Long
	 */
	private byte[] longToBytes(Long value) {
		return ByteBuffer.allocate(Long.BYTES).putLong(value).array();
	}
}
