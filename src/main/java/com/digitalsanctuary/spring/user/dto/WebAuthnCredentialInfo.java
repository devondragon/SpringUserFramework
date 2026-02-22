package com.digitalsanctuary.spring.user.dto;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for WebAuthn credential information displayed to users.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebAuthnCredentialInfo {

	/** Credential ID. */
	private String id;

	/** User-friendly label. */
	private String label;

	/** Creation date. */
	private Instant created;

	/** Last authentication date. */
	private Instant lastUsed;

	/** Supported transports (usb, nfc, ble, internal). */
	private List<String> transports;

	/** Whether credential is backup-eligible (synced passkey). */
	private Boolean backupEligible;

	/** Whether credential is currently backed up. */
	private Boolean backupState;
}
