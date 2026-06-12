package com.digitalsanctuary.spring.user.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.authorization.AllRequiredFactorsAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.DefaultAuthorizationManagerFactory;
import org.springframework.security.authorization.RequiredFactor;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Configuration that registers {@link MfaConfigProperties} and provides MFA-related beans.
 * <p>
 * This configuration is always active because {@code WebSecurityConfig} requires {@link MfaConfigProperties} regardless
 * of whether MFA is enabled. The MFA beans are only created when MFA is enabled.
 * </p>
 *
 * <h2>Spike conclusion (Spring Security 7.0.5 factor merging, H4)</h2>
 * <p>
 * Multi-factor login has two distinct sides in SS7 and this framework was only wiring one of them:
 * </p>
 * <ol>
 * <li><b>Enforcement</b> &mdash; the {@link DefaultAuthorizationManagerFactory} produced by
 * {@link #mfaAuthorizationManagerFactory()} sets an {@link AllRequiredFactorsAuthorizationManager} (AND semantics) so
 * {@code .authenticated()} additionally requires every <em>configured</em> factor authority. This was already present
 * and correct, scoped to the property-driven subset (so a {@code PASSWORD}-only deployment is not locked out).</li>
 * <li><b>Merging</b> &mdash; the missing half. SS7 merges factor authorities across login steps inside
 * {@code org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter#doFilter}: when its
 * {@code mfaEnabled} flag is {@code true} and an already-authenticated context exists for the same principal, the new
 * authentication result is rebuilt via {@code authenticationResult.toBuilder().authorities(...)} to additively carry the
 * authorities of the existing authentication. That merged result is then passed through {@code successfulAuthentication}
 * to the success handler. Without {@code mfaEnabled=true}, completing a second factor REPLACES the authentication
 * (dropping the first factor) and the user can never satisfy "all required factors" &mdash; the H4 lockout.</li>
 * </ol>
 * <p>
 * The {@code mfaEnabled} flag is normally flipped by {@code @EnableMultiFactorAuthentication}, whose
 * {@code MultiFactorAuthenticationSelector} imports {@code AuthorizationManagerFactoryConfiguration} (a second, static
 * {@link DefaultAuthorizationManagerFactory} bean built from the annotation's STATIC {@code authorities()} superset) and
 * {@code EnableMfaFiltersConfiguration} (an {@code EnableMfaFiltersPostProcessor} that calls
 * {@code setMfaEnabled(true)} on every authentication processing filter). We deliberately do NOT use the annotation here
 * for two reasons: (a) its {@code authorities()} is a static superset, but AllRequiredFactors is AND-enforcement, so a
 * superset would lock out subset deployments (e.g. {@code PASSWORD}-only); and (b) its factory bean is registered
 * by-type with no {@code @ConditionalOnMissingBean}, which would collide with our property-driven factory &mdash;
 * {@code AuthorizeHttpRequestsConfigurer} resolves the factory via {@code getBeanProvider(...).getIfAvailable()}, which
 * returns {@code null} on ambiguity and silently falls back to a non-enforcing default, disabling factor enforcement.
 * </p>
 * <p>
 * Therefore we keep our single property-driven enforcement factory and activate ONLY the merging side using public
 * SS7 API: {@link #mfaFilterMergingPostProcessor()} replicates {@code EnableMfaFiltersPostProcessor} by invoking the
 * public {@link AbstractAuthenticationProcessingFilter#setMfaEnabled(boolean)} on the form-login and WebAuthn
 * processing filters. This is gated on {@code user.mfa.enabled=true}, leaving the default (no-MFA) path untouched.
 * </p>
 *
 * @see MfaConfigProperties
 * @see WebSecurityConfig
 */
@Slf4j
@Configuration
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

		// Unknown/blank factors are silently skipped here; validateMfaConfiguration() will
		// throw before startup completes if the configuration is invalid.
		for (String factor : mfaConfigProperties.getFactors()) {
			if (factor == null || factor.isBlank()) {
				continue;
			}
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
	 * Activates Spring Security 7 factor merging by enabling MFA mode on every authentication processing filter.
	 * <p>
	 * This replicates the behaviour of {@code @EnableMultiFactorAuthentication}'s internal
	 * {@code EnableMfaFiltersPostProcessor} using only public API. When MFA mode is enabled,
	 * {@link AbstractAuthenticationProcessingFilter} merges the factor authorities of the existing authentication onto
	 * the authentication produced by a subsequent login step (for the same principal) instead of replacing it. Without
	 * this, completing a second factor would drop the first factor's authority and the user could never satisfy all
	 * required factors (H4 lockout).
	 * </p>
	 * <p>
	 * The bean is only created when MFA is enabled, so the default (no-MFA) login path is completely unaffected.
	 * </p>
	 *
	 * @return a {@link BeanPostProcessor} that calls {@code setMfaEnabled(true)} on authentication processing filters
	 */
	@Bean
	@ConditionalOnProperty(name = "user.mfa.enabled", havingValue = "true", matchIfMissing = false)
	public static BeanPostProcessor mfaFilterMergingPostProcessor() {
		return new BeanPostProcessor() {
			@Override
			public Object postProcessAfterInitialization(Object bean, String beanName) {
				// Intentionally scoped to AbstractAuthenticationProcessingFilter, which covers every
				// authentication mechanism this framework configures: formLogin, webAuthn, and oauth2Login
				// all extend it. SS's internal EnableMfaFiltersPostProcessor additionally flips the flag on
				// AuthenticationFilter, BasicAuthenticationFilter, and pre-authentication filters; this
				// framework does not configure those mechanisms, so they are deliberately not targeted here.
				if (bean instanceof AbstractAuthenticationProcessingFilter filter) {
					filter.setMfaEnabled(true);
					log.debug("MFA factor merging enabled on filter: {}", bean.getClass().getName());
				}
				return bean;
			}
		};
	}

	/**
	 * Validates MFA configuration on application startup. Performs validation only when MFA is enabled; returns
	 * immediately otherwise.
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
			if (factor == null || factor.isBlank()) {
				throw new IllegalStateException(
						"MFA factors list contains a null or blank entry. Check user.mfa.factors configuration.");
			}
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
	 * <p>
	 * Package-private for testing via {@link MfaConfigurationTest}.
	 * </p>
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
	public static String mapFactorToAuthority(String factorName) {
		if (factorName == null || factorName.isBlank()) {
			return null;
		}
		return FACTOR_AUTHORITY_MAP.get(factorName.toUpperCase());
	}
}
