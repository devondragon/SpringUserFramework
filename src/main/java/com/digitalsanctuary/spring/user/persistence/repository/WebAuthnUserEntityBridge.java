package com.digitalsanctuary.spring.user.persistence.repository;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.Optional;
import org.springframework.context.annotation.Primary;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.model.WebAuthnUserEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 * Primary {@link PublicKeyCredentialUserEntityRepository} that bridges Spring Security's WebAuthn system with the
 * Spring User Framework's User entity. It handles edge cases like anonymousUser and null usernames, and automatically
 * creates WebAuthn user entities for existing application users.
 * </p>
 *
 * <p>
 * Marked as {@code @Primary} so Spring Security's WebAuthn filters use this bridge instead of any auto-configured
 * repository.
 * </p>
 */
@Repository
@Primary
@RequiredArgsConstructor
@Slf4j
public class WebAuthnUserEntityBridge implements PublicKeyCredentialUserEntityRepository {

	private final WebAuthnUserEntityRepository webAuthnUserEntityRepository;
	private final UserRepository userRepository;

	@Override
	public PublicKeyCredentialUserEntity findById(Bytes id) {
		String idStr = Base64.getUrlEncoder().withoutPadding().encodeToString(id.getBytes());
		return webAuthnUserEntityRepository.findById(idStr).map(this::toSpringSecurityEntity).orElse(null);
	}

	@Override
	public PublicKeyCredentialUserEntity findByUsername(String username) {
		// Handle edge cases that can occur during login
		if (username == null || username.isEmpty() || "anonymousUser".equals(username)) {
			log.debug("Ignoring invalid username: {}", username);
			return null;
		}

		// Check if user entity already exists
		Optional<WebAuthnUserEntity> existing = webAuthnUserEntityRepository.findByName(username);
		if (existing.isPresent()) {
			return toSpringSecurityEntity(existing.get());
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
	@Transactional
	public void save(PublicKeyCredentialUserEntity userEntity) {
		String idStr = Base64.getUrlEncoder().withoutPadding().encodeToString(userEntity.getId().getBytes());

		WebAuthnUserEntity entity = webAuthnUserEntityRepository.findById(idStr).orElseGet(WebAuthnUserEntity::new);
		entity.setId(idStr);
		entity.setName(userEntity.getName());
		entity.setDisplayName(userEntity.getDisplayName());

		// If the entity doesn't have a user link yet, try to find the app user by email
		if (entity.getUser() == null) {
			User appUser = userRepository.findByEmail(userEntity.getName());
			if (appUser != null) {
				entity.setUser(appUser);
			}
		}

		webAuthnUserEntityRepository.save(entity);
	}

	@Override
	@Transactional
	public void delete(Bytes id) {
		String idStr = Base64.getUrlEncoder().withoutPadding().encodeToString(id.getBytes());
		webAuthnUserEntityRepository.deleteById(idStr);
	}

	/**
	 * Create user entity from User model and link to user_account.
	 *
	 * @param user the User entity
	 * @return the created PublicKeyCredentialUserEntity
	 */
	@Transactional
	public PublicKeyCredentialUserEntity createUserEntity(User user) {
		Bytes userId = new Bytes(longToBytes(user.getId()));
		String idStr = Base64.getUrlEncoder().withoutPadding().encodeToString(userId.getBytes());
		String displayName = user.getFullName();

		WebAuthnUserEntity entity = new WebAuthnUserEntity();
		entity.setId(idStr);
		entity.setName(user.getEmail());
		entity.setDisplayName(displayName);
		entity.setUser(user);

		webAuthnUserEntityRepository.save(entity);

		log.info("Created WebAuthn user entity for user: {}", user.getEmail());

		return ImmutablePublicKeyCredentialUserEntity.builder().name(user.getEmail()).id(userId)
				.displayName(displayName).build();
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
	 * Convert a JPA entity to Spring Security's PublicKeyCredentialUserEntity.
	 *
	 * @param entity the JPA entity
	 * @return the Spring Security entity
	 */
	private PublicKeyCredentialUserEntity toSpringSecurityEntity(WebAuthnUserEntity entity) {
		byte[] idBytes = Base64.getUrlDecoder().decode(entity.getId());
		return ImmutablePublicKeyCredentialUserEntity.builder().name(entity.getName()).id(new Bytes(idBytes))
				.displayName(entity.getDisplayName()).build();
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
