package com.digitalsanctuary.spring.user.listener;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import com.digitalsanctuary.spring.user.event.UserPreDeleteEvent;
import com.digitalsanctuary.spring.user.persistence.model.WebAuthnUserEntity;
import com.digitalsanctuary.spring.user.persistence.repository.WebAuthnCredentialRepository;
import com.digitalsanctuary.spring.user.persistence.repository.WebAuthnUserEntityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Listens for {@link UserPreDeleteEvent} and cleans up all WebAuthn data for the user being deleted.
 *
 * <p>
 * Deletes all credentials associated with the user's WebAuthn entity, then deletes the entity itself.
 * This listener is synchronous so that failures cause the enclosing transaction to roll back, preventing
 * orphaned user accounts.
 * </p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "user.webauthn.enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
public class WebAuthnPreDeleteEventListener {

	private final WebAuthnCredentialRepository credentialRepository;
	private final WebAuthnUserEntityRepository userEntityRepository;

	/**
	 * Handle user pre-delete by removing all WebAuthn credentials and the user entity.
	 *
	 * @param event the user pre-delete event
	 */
	@EventListener
	public void onUserPreDelete(UserPreDeleteEvent event) {
		Long userId = event.getUserId();
		log.debug("Cleaning up WebAuthn data for user {}", userId);

		userEntityRepository.findByUserId(userId).ifPresent(this::deleteUserEntityAndCredentials);
	}

	private void deleteUserEntityAndCredentials(WebAuthnUserEntity userEntity) {
		credentialRepository.findByUserEntity(userEntity).forEach(credentialRepository::delete);
		userEntityRepository.delete(userEntity);
		log.info("Deleted WebAuthn data for user entity {}", userEntity.getName());
	}
}
