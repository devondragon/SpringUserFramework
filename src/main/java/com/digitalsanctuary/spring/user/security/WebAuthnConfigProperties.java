package com.digitalsanctuary.spring.user.security;

import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Data;

/**
 * Configuration properties for WebAuthn (Passkey) authentication.
 */
@Data
@ConfigurationProperties(prefix = "user.webauthn")
public class WebAuthnConfigProperties {

	/**
	 * Relying Party ID.
	 */
	private String rpId = "localhost";

	/**
	 * Relying Party Name.
	 */
	private String rpName = "Spring User Framework";

	/**
	 * Allowed origins for WebAuthn operations.
	 */
	private Set<String> allowedOrigins = Set.of("https://localhost:8443");

	/**
	 * Whether Passkey support is enabled.
	 */
	private boolean enabled = false;

	/**
	 * Whether to email the account owner when a passkey is registered on their account. Enrolling a passkey grants a
	 * durable new way into the account that survives a password change, so the owner is told by default. Set to
	 * {@code false} only if your application sends its own equivalent notification.
	 */
	private boolean notifyOnRegistration = true;
}
