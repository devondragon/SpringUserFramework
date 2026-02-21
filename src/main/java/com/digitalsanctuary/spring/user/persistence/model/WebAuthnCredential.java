package com.digitalsanctuary.spring.user.persistence.model;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * JPA entity for the {@code user_credentials} table. Stores WebAuthn credentials (public keys) for passkey
 * authentication. The {@code credentialId} is stored as a Base64url string matching Spring Security's convention.
 */
@Data
@Entity
@Table(name = "user_credentials")
public class WebAuthnCredential {

	/** Credential ID as Base64url string (matches Spring Security's storage convention). */
	@Id
	@Column(name = "credential_id", length = 512)
	private String credentialId;

	/** FK to the WebAuthn user entity. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_entity_user_id", nullable = false)
	private WebAuthnUserEntity userEntity;

	/** COSE-encoded public key (typically 77-300 bytes, RSA keys can be larger). */
	@Lob
	@Column(name = "public_key", nullable = false, columnDefinition = "BLOB")
	private byte[] publicKey;

	/** Counter to detect cloned authenticators. */
	@Column(name = "signature_count", nullable = false)
	private long signatureCount;

	/** User verification performed during registration. */
	@Column(name = "uv_initialized", nullable = false)
	private boolean uvInitialized;

	/** Credential can be synced (iCloud Keychain, etc.). */
	@Column(name = "backup_eligible", nullable = false)
	private boolean backupEligible;

	/** Supported transports: usb, nfc, ble, internal. */
	@Column(name = "authenticator_transports", length = 1000)
	private String authenticatorTransports;

	/** Credential type (e.g. public-key). */
	@Column(name = "public_key_credential_type", length = 100)
	private String publicKeyCredentialType;

	/** Credential is currently backed up. */
	@Column(name = "backup_state", nullable = false)
	private boolean backupState;

	/** Attestation data from registration (can be several KB). */
	@Lob
	@Column(name = "attestation_object", columnDefinition = "BLOB")
	private byte[] attestationObject;

	/** Client data JSON from registration (can be several KB). */
	@Lob
	@Column(name = "attestation_client_data_json", columnDefinition = "BLOB")
	private byte[] attestationClientDataJson;

	/** Creation timestamp. */
	@Column(nullable = false)
	private Instant created;

	/** Last authentication timestamp. */
	@Column(name = "last_used")
	private Instant lastUsed;

	/** User-friendly name (e.g., "My iPhone", "YubiKey"). */
	@Column(length = 1000, nullable = false)
	private String label;
}
