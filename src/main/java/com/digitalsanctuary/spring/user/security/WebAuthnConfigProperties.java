package com.digitalsanctuary.spring.user.security;

import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;
import lombok.Data;

/**
 * Configuration properties for WebAuthn (Passkey) authentication.
 */
@Data
@Component
@PropertySource("classpath:config/dsspringuserconfig.properties")
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
}
