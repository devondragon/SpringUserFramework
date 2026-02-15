package com.digitalsanctuary.spring.user.persistence.repository;

import java.nio.ByteBuffer;
import java.util.Optional;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.JdbcPublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import com.digitalsanctuary.spring.user.persistence.model.User;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 * Primary {@link PublicKeyCredentialUserEntityRepository} that bridges Spring Security's WebAuthn system with the Spring User Framework's User
 * entity. It handles edge cases like anonymousUser and null usernames, and automatically creates WebAuthn user entities for existing application
 * users.
 * </p>
 *
 * <p>
 * Marked as {@code @Primary} so Spring Security's WebAuthn filters use this bridge instead of the bare JDBC repository.
 * </p>
 */
@Repository
@Primary
@Slf4j
public class WebAuthnUserEntityBridge implements PublicKeyCredentialUserEntityRepository {

	private final JdbcPublicKeyCredentialUserEntityRepository delegate;
	private final JdbcTemplate jdbcTemplate;
	private final UserRepository userRepository;

	/**
	 * Constructor creates the JDBC delegate internally to avoid circular bean dependency.
	 *
	 * @param jdbcTemplate the JDBC template
	 * @param userRepository the user repository
	 */
	public WebAuthnUserEntityBridge(JdbcTemplate jdbcTemplate, UserRepository userRepository) {
		this.jdbcTemplate = jdbcTemplate;
		this.userRepository = userRepository;
		this.delegate = new JdbcPublicKeyCredentialUserEntityRepository(jdbcTemplate);
	}

	@Override
	public PublicKeyCredentialUserEntity findById(Bytes id) {
		return delegate.findById(id);
	}

	@Override
	public PublicKeyCredentialUserEntity findByUsername(String username) {
		// Handle edge cases that can occur during login
		if (username == null || username.isEmpty() || "anonymousUser".equals(username)) {
			log.debug("Ignoring invalid username: {}", username);
			return null;
		}

		// Check if user entity already exists
		PublicKeyCredentialUserEntity existing = delegate.findByUsername(username);
		if (existing != null) {
			return existing;
		}

		// User entity doesn't exist yet - check if application user exists
		User user = userRepository.findByEmail(username);
		if (user == null) {
			log.debug("No application user found for username: {}", username);
			return null;
		}

		// Create WebAuthn user entity for this application user
		return createUserEntity(user);
	}

	@Override
	public void save(PublicKeyCredentialUserEntity userEntity) {
		delegate.save(userEntity);
	}

	@Override
	public void delete(Bytes id) {
		delegate.delete(id);
	}

	/**
	 * Create user entity from User model and link to user_account via user_account_id.
	 *
	 * @param user the User entity
	 * @return the created PublicKeyCredentialUserEntity
	 */
	@Transactional
	public PublicKeyCredentialUserEntity createUserEntity(User user) {
		Bytes userId = new Bytes(longToBytes(user.getId()));
		String displayName = user.getFullName();

		PublicKeyCredentialUserEntity entity = ImmutablePublicKeyCredentialUserEntity.builder().name(user.getEmail()).id(userId)
				.displayName(displayName).build();

		// Let Spring Security's JDBC repository do the standard INSERT
		delegate.save(entity);

		// Set our custom user_account_id column to link to the app user
		jdbcTemplate.update("UPDATE user_entities SET user_account_id = ? WHERE name = ?", user.getId(), user.getEmail());

		log.info("Created WebAuthn user entity for user: {}", user.getEmail());
		return entity;
	}

	/**
	 * Find by username, returning Optional for internal use.
	 *
	 * @param username the username (email) to look up
	 * @return Optional containing the PublicKeyCredentialUserEntity, or empty if not found
	 */
	public Optional<PublicKeyCredentialUserEntity> findOptionalByUsername(String username) {
		PublicKeyCredentialUserEntity entity = findByUsername(username);
		return Optional.ofNullable(entity);
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
