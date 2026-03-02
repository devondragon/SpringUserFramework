package com.digitalsanctuary.spring.user.security;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Data;

/**
 * Configuration properties for Multi-Factor Authentication (MFA).
 * <p>
 * When enabled, all authenticated endpoints require all configured factors to be satisfied. Spring Security 7's built-in
 * MFA infrastructure handles enforcement, redirection between factor login pages, and session management automatically.
 * </p>
 * <p>
 * Example configuration:
 * </p>
 *
 * <pre>
 * user.mfa.enabled: true
 * user.mfa.factors: PASSWORD, WEBAUTHN
 * user.mfa.passwordEntryPointUri: /user/login.html
 * user.mfa.webauthnEntryPointUri: /user/webauthn/login.html
 * </pre>
 *
 * @see MfaConfiguration
 */
@Data
@ConfigurationProperties(prefix = "user.mfa")
public class MfaConfigProperties {

	/**
	 * Whether MFA is enabled. When true, all authenticated endpoints require all configured factors.
	 */
	private boolean enabled = false;

	/**
	 * The list of authentication factors required for MFA. Supported values: PASSWORD, WEBAUTHN.
	 */
	private List<String> factors = new ArrayList<>();

	/**
	 * The URI to redirect to when the PASSWORD factor is missing. This should point to the password login page.
	 */
	private String passwordEntryPointUri = "/user/login.html";

	/**
	 * The URI to redirect to when the WEBAUTHN factor is missing. This should point to the WebAuthn/passkey login page.
	 */
	private String webauthnEntryPointUri = "/user/webauthn/login.html";
}
