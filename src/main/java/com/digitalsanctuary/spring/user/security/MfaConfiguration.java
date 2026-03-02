package com.digitalsanctuary.spring.user.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.authorization.AllRequiredFactorsAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.DefaultAuthorizationManagerFactory;
import org.springframework.security.authorization.RequiredFactor;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Configuration that registers {@link MfaConfigProperties} and provides MFA-related beans.
 * <p>
 * This configuration is always active because {@code WebSecurityConfig} requires {@link MfaConfigProperties} regardless
 * of whether MFA is enabled. The {@code DefaultAuthorizationManagerFactory} bean is only created when MFA is enabled.
 * </p>
 * <p>
 * When enabled, the {@code DefaultAuthorizationManagerFactory} is configured with an
 * {@link AllRequiredFactorsAuthorizationManager} that makes {@code .authenticated()} in
 * {@code authorizeHttpRequests} additionally require all configured factor authorities. Spring Security 7's built-in
 * infrastructure handles enforcement and session management automatically.
 * </p>
 *
 * @see MfaConfigProperties
 * @see WebSecurityConfig
 */
@Slf4j
@Configuration
@PropertySource("classpath:config/dsspringuserconfig.properties")
@EnableConfigurationProperties(MfaConfigProperties.class)
@RequiredArgsConstructor
public class MfaConfiguration {

	/**
	 * Mapping from user-facing factor names to Spring Security {@link FactorGrantedAuthority} authority strings.
	 */
	private static final Map<String, String> FACTOR_AUTHORITY_MAP = Map.of(
			"PASSWORD", FactorGrantedAuthority.PASSWORD_AUTHORITY,
			"WEBAUTHN", FactorGrantedAuthority.WEBAUTHN_AUTHORITY);

	private final MfaConfigProperties mfaConfigProperties;
	private final WebAuthnConfigProperties webAuthnConfigProperties;

	/**
	 * Creates a {@link DefaultAuthorizationManagerFactory} with an additional authorization requirement for all
	 * configured MFA factors. This makes {@code .authenticated()} in {@code authorizeHttpRequests} require all
	 * configured factors to be satisfied.
	 *
	 * @return the authorization manager factory configured with required factor authorities
	 */
	@Bean
	@ConditionalOnProperty(name = "user.mfa.enabled", havingValue = "true", matchIfMissing = false)
	public DefaultAuthorizationManagerFactory<Object> mfaAuthorizationManagerFactory() {
		AllRequiredFactorsAuthorizationManager.Builder<Object> factorsBuilder =
				AllRequiredFactorsAuthorizationManager.builder();

		for (String factor : mfaConfigProperties.getFactors()) {
			String authority = FACTOR_AUTHORITY_MAP.get(factor.toUpperCase());
			if (authority != null) {
				factorsBuilder.requireFactor(RequiredFactor.withAuthority(authority).build());
			}
		}

		AuthorizationManager<Object> factorsManager = factorsBuilder.build();

		DefaultAuthorizationManagerFactory<Object> factory = new DefaultAuthorizationManagerFactory<>();
		factory.setAdditionalAuthorization(factorsManager);

		log.info("MFA enabled with required factors: {}", mfaConfigProperties.getFactors());
		return factory;
	}

	/**
	 * Validates MFA configuration on application startup. Runs only when MFA is enabled.
	 *
	 * @param event the context refreshed event
	 */
	@EventListener(ContextRefreshedEvent.class)
	public void validateMfaConfiguration(ContextRefreshedEvent event) {
		if (!mfaConfigProperties.isEnabled()) {
			return;
		}

		List<String> factors = mfaConfigProperties.getFactors();

		if (factors == null || factors.isEmpty()) {
			throw new IllegalStateException(
					"MFA is enabled (user.mfa.enabled=true) but no factors are configured. "
							+ "Set user.mfa.factors to a comma-separated list of factors (e.g., PASSWORD,WEBAUTHN).");
		}

		for (String factor : factors) {
			if (!FACTOR_AUTHORITY_MAP.containsKey(factor.toUpperCase())) {
				throw new IllegalStateException(
						"Unknown MFA factor: '" + factor + "'. Supported factors: " + FACTOR_AUTHORITY_MAP.keySet());
			}
		}

		if (factors.stream().anyMatch(f -> "WEBAUTHN".equalsIgnoreCase(f)) && !webAuthnConfigProperties.isEnabled()) {
			throw new IllegalStateException(
					"MFA factor WEBAUTHN is configured but WebAuthn is disabled (user.webauthn.enabled=false). "
							+ "Enable WebAuthn or remove WEBAUTHN from user.mfa.factors.");
		}

		if (factors.stream().anyMatch(f -> "PASSWORD".equalsIgnoreCase(f))) {
			log.warn("MFA factor PASSWORD is configured. Users with passwordless (passkey-only) accounts "
					+ "will not be able to satisfy the PASSWORD factor. Consider your account types carefully.");
		}
	}

	/**
	 * Resolves the configured factor names to Spring Security authority strings.
	 *
	 * @return list of Spring Security authority strings
	 */
	List<String> resolveFactorAuthorities() {
		List<String> authorities = new ArrayList<>();
		for (String factor : mfaConfigProperties.getFactors()) {
			String authority = FACTOR_AUTHORITY_MAP.get(factor.toUpperCase());
			if (authority != null) {
				authorities.add(authority);
			}
		}
		return authorities;
	}

	/**
	 * Maps a user-facing factor name to a Spring Security authority string.
	 *
	 * @param factorName the factor name (e.g., "PASSWORD", "WEBAUTHN")
	 * @return the corresponding Spring Security authority string, or null if unknown
	 */
	static String mapFactorToAuthority(String factorName) {
		return FACTOR_AUTHORITY_MAP.get(factorName.toUpperCase());
	}
}
