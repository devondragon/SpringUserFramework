package com.digitalsanctuary.spring.user.security;

import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.webauthn.api.AuthenticatorTransport;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutableCredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCose;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialType;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.transaction.annotation.Transactional;
import com.digitalsanctuary.spring.user.persistence.model.WebAuthnCredential;
import com.digitalsanctuary.spring.user.persistence.model.WebAuthnUserEntity;
import com.digitalsanctuary.spring.user.persistence.repository.WebAuthnCredentialRepository;
import com.digitalsanctuary.spring.user.persistence.repository.WebAuthnUserEntityRepository;
import lombok.extern.slf4j.Slf4j;

/**
 * Configuration for WebAuthn repositories.
 *
 * <p>
 * Note: The {@code PublicKeyCredentialUserEntityRepository} bean is provided by
 * {@link com.digitalsanctuary.spring.user.persistence.repository.WebAuthnUserEntityBridge} which is marked as
 * {@code @Primary} and bridges Spring Security's WebAuthn user entities with the application's User model.
 * </p>
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "user.webauthn.enabled", havingValue = "true", matchIfMissing = false)
public class WebAuthnRepositoryConfig {

	/**
	 * JPA-backed {@link UserCredentialRepository} that handles credential CRUD operations including:
	 * <ul>
	 * <li>save() - Store new credentials after registration to user_credentials table</li>
	 * <li>findByCredentialId() - Look up credentials during authentication</li>
	 * <li>findByUserId() - Get all credentials for a user</li>
	 * <li>delete() - Remove credentials from database</li>
	 * </ul>
	 *
	 * @param credentialRepository JPA repository for WebAuthn credentials
	 * @param userEntityRepository JPA repository for WebAuthn user entities
	 * @return the UserCredentialRepository instance
	 */
	@Bean
	public UserCredentialRepository userCredentialRepository(WebAuthnCredentialRepository credentialRepository,
			WebAuthnUserEntityRepository userEntityRepository) {
		log.info("Initializing JPA-backed WebAuthn UserCredentialRepository");
		return new JpaUserCredentialRepository(credentialRepository, userEntityRepository);
	}

	/**
	 * JPA-backed implementation of Spring Security's {@link UserCredentialRepository}. Converts between Spring
	 * Security's {@link CredentialRecord}/{@link Bytes} types and JPA entity fields.
	 */
	@Slf4j
	static class JpaUserCredentialRepository implements UserCredentialRepository {

		private final WebAuthnCredentialRepository credentialRepository;
		private final WebAuthnUserEntityRepository userEntityRepository;

		JpaUserCredentialRepository(WebAuthnCredentialRepository credentialRepository,
				WebAuthnUserEntityRepository userEntityRepository) {
			this.credentialRepository = credentialRepository;
			this.userEntityRepository = userEntityRepository;
		}

		@Override
		@Transactional
		public void save(CredentialRecord record) {
			String credIdStr = toBase64Url(record.getCredentialId().getBytes());

			WebAuthnCredential entity = credentialRepository.findById(credIdStr).orElseGet(WebAuthnCredential::new);
			entity.setCredentialId(credIdStr);

			// Look up the user entity
			String userEntityId = toBase64Url(record.getUserEntityUserId().getBytes());
			WebAuthnUserEntity userEntity = userEntityRepository.findById(userEntityId).orElseThrow(
					() -> new IllegalStateException("WebAuthn user entity not found for ID: " + userEntityId));
			entity.setUserEntity(userEntity);

			entity.setPublicKey(record.getPublicKey().getBytes());
			entity.setSignatureCount(record.getSignatureCount());
			entity.setUvInitialized(record.isUvInitialized());
			entity.setBackupEligible(record.isBackupEligible());
			entity.setBackupState(record.isBackupState());
			entity.setPublicKeyCredentialType(
					record.getCredentialType() != null ? record.getCredentialType().getValue() : null);
			entity.setAuthenticatorTransports(transportsToString(record.getTransports()));
			entity.setAttestationObject(
					record.getAttestationObject() != null ? record.getAttestationObject().getBytes() : null);
			entity.setAttestationClientDataJson(record.getAttestationClientDataJSON() != null
					? record.getAttestationClientDataJSON().getBytes()
					: null);
			entity.setCreated(record.getCreated());
			entity.setLastUsed(record.getLastUsed());
			entity.setLabel(record.getLabel());

			credentialRepository.save(entity);
		}

		@Override
		public CredentialRecord findByCredentialId(Bytes credentialId) {
			String credIdStr = toBase64Url(credentialId.getBytes());
			return credentialRepository.findById(credIdStr).map(this::toCredentialRecord).orElse(null);
		}

		@Override
		public List<CredentialRecord> findByUserId(Bytes userEntityUserId) {
			String userEntityId = toBase64Url(userEntityUserId.getBytes());
			return credentialRepository.findByUserEntityId(userEntityId).stream().map(this::toCredentialRecord)
					.collect(Collectors.toList());
		}

		@Override
		@Transactional
		public void delete(Bytes credentialId) {
			String credIdStr = toBase64Url(credentialId.getBytes());
			credentialRepository.deleteById(credIdStr);
		}

		/**
		 * Convert a JPA entity to Spring Security's CredentialRecord.
		 */
		private CredentialRecord toCredentialRecord(WebAuthnCredential entity) {
			return ImmutableCredentialRecord.builder()
					.credentialId(new Bytes(Base64.getUrlDecoder().decode(entity.getCredentialId())))
					.userEntityUserId(new Bytes(Base64.getUrlDecoder().decode(entity.getUserEntity().getId())))
					.publicKey(new ImmutablePublicKeyCose(entity.getPublicKey()))
					.signatureCount(entity.getSignatureCount()).uvInitialized(entity.isUvInitialized())
					.backupEligible(entity.isBackupEligible()).backupState(entity.isBackupState())
					.credentialType(entity.getPublicKeyCredentialType() != null
							? PublicKeyCredentialType.valueOf(entity.getPublicKeyCredentialType())
							: null)
					.transports(parseTransports(entity.getAuthenticatorTransports()))
					.attestationObject(
							entity.getAttestationObject() != null ? new Bytes(entity.getAttestationObject()) : null)
					.attestationClientDataJSON(entity.getAttestationClientDataJson() != null
							? new Bytes(entity.getAttestationClientDataJson())
							: null)
					.created(entity.getCreated()).lastUsed(entity.getLastUsed()).label(entity.getLabel()).build();
		}

		/**
		 * Parse comma-separated transport string to a Set of AuthenticatorTransport.
		 */
		private Set<AuthenticatorTransport> parseTransports(String transports) {
			if (transports == null || transports.isEmpty()) {
				return Collections.emptySet();
			}
			return Arrays.stream(transports.split(",")).map(String::trim).filter(s -> !s.isEmpty())
					.map(AuthenticatorTransport::valueOf).collect(Collectors.toSet());
		}

		/**
		 * Convert a Set of AuthenticatorTransport to a comma-separated string.
		 */
		private String transportsToString(Set<AuthenticatorTransport> transports) {
			if (transports == null || transports.isEmpty()) {
				return null;
			}
			return transports.stream().map(AuthenticatorTransport::getValue).collect(Collectors.joining(","));
		}

		/**
		 * Encode bytes to Base64url without padding.
		 */
		private String toBase64Url(byte[] bytes) {
			return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		}
	}
}
