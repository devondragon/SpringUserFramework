package com.digitalsanctuary.spring.user.security;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;
import com.digitalsanctuary.spring.user.roles.RolesAndPrivilegesConfig;
import com.digitalsanctuary.spring.user.service.WebAuthnCredentialManagementService;
import lombok.extern.slf4j.Slf4j;

/**
 * Registers the framework's built-in {@link StepUpService} when {@code user.security.stepUp.enabled=true}.
 *
 * <p>
 * The bean backs off entirely when a consuming application supplies its own {@code StepUpService}, so an application
 * with a bespoke step-up mechanism keeps it. When step-up is disabled no bean is registered at all, which leaves
 * {@code POST /user/setPassword} governed by {@code user.security.allowInitialPasswordSetWithoutStepUp} and passkey
 * delete/rename unchanged.
 * </p>
 *
 * <p>
 * The factor-authority name check registered here runs regardless of whether step-up is enabled, because a colliding
 * authority name breaks MFA as well. See {@link FactorAuthorityNameValidator}.
 * </p>
 */
@Slf4j
@AutoConfiguration
@PropertySource("classpath:config/dsspringuserconfig.properties")
@EnableConfigurationProperties(StepUpConfigProperties.class)
public class StepUpAutoConfiguration {

    /**
     * Creates the built-in step-up service, validating the configured factor names first.
     *
     * @param config the step-up configuration
     * @param webAuthnConfigProperties the WebAuthn configuration, so a WEBAUTHN factor that no login flow could
     *        ever issue is rejected at startup rather than silently making step-up inert
     * @param credentialServiceProvider provides the WebAuthn credential service when WebAuthn is enabled, so the
     *        service can tell an account with no passkey apart from one that simply has not asserted
     * @return the built-in {@link StepUpService}
     * @throws IllegalStateException if {@code user.security.stepUp.factors} is empty or names an unknown factor
     */
    @Bean
    @ConditionalOnMissingBean(StepUpService.class)
    @ConditionalOnProperty(prefix = "user.security.step-up", name = "enabled", havingValue = "true")
    public StepUpService dsFactorFreshnessStepUpService(StepUpConfigProperties config,
            WebAuthnConfigProperties webAuthnConfigProperties,
            ObjectProvider<WebAuthnCredentialManagementService> credentialServiceProvider) {
        validateFactors(config.getFactors(), webAuthnConfigProperties);
        log.info("Step-up enabled: a factor of {} issued within {}s authorizes credential-altering operations",
                config.getFactors(), config.getTtlSeconds());
        // Resolved per call rather than captured: WebAuthnCredentialManagementService is conditional on
        // user.webauthn.enabled, and when it is absent no account can hold a passkey.
        return new DSFactorFreshnessStepUpService(config, user -> {
            WebAuthnCredentialManagementService credentialService = credentialServiceProvider.getIfAvailable();
            return credentialService != null && credentialService.hasCredentials(user);
        });
    }

    /**
     * Creates the startup check that rejects role and privilege names colliding with Spring Security factor
     * authorities.
     *
     * @param rolesAndPrivilegesConfig the configured roles and privileges
     * @param mfaConfigProperties the MFA configuration
     * @param stepUpConfigProperties the step-up configuration
     * @return the validator
     */
    @Bean
    @ConditionalOnMissingBean(FactorAuthorityNameValidator.class)
    public FactorAuthorityNameValidator factorAuthorityNameValidator(RolesAndPrivilegesConfig rolesAndPrivilegesConfig,
            MfaConfigProperties mfaConfigProperties, StepUpConfigProperties stepUpConfigProperties) {
        return new FactorAuthorityNameValidator(rolesAndPrivilegesConfig, mfaConfigProperties, stepUpConfigProperties);
    }

    /**
     * Fails startup rather than at the first gated request: a typo here would otherwise surface as an operation nobody
     * can ever perform, since the required factor is never issued.
     */
    private static void validateFactors(List<String> factors, WebAuthnConfigProperties webAuthnConfigProperties) {
        if (factors == null || factors.isEmpty()) {
            throw new IllegalStateException("Step-up is enabled (user.security.stepUp.enabled=true) but no factors are configured. "
                    + "Set user.security.stepUp.factors to one or more of " + knownFactors() + ".");
        }
        Set<String> unknown = factors.stream().filter(factor -> factor == null || factor.isBlank()
                || !StepUpConfigProperties.FACTOR_AUTHORITIES.containsKey(factor.toUpperCase(Locale.ROOT)))
                .map(factor -> factor == null ? "null" : factor).collect(Collectors.toCollection(TreeSet::new));
        if (!unknown.isEmpty()) {
            throw new IllegalStateException("Unknown step-up factor(s) " + unknown + " in user.security.stepUp.factors. "
                    + "Valid values are " + knownFactors() + ".");
        }
        if (factors.stream().anyMatch(factor -> "WEBAUTHN".equalsIgnoreCase(factor)) && !webAuthnConfigProperties.isEnabled()) {
            throw new IllegalStateException(
                    "Step-up factor WEBAUTHN is configured but WebAuthn is disabled (user.webauthn.enabled=false). "
                            + "No account could ever produce that factor, so step-up would never apply. "
                            + "Enable WebAuthn or remove WEBAUTHN from user.security.stepUp.factors.");
        }
        if (factors.stream().anyMatch(factor -> "PASSWORD".equalsIgnoreCase(factor))) {
            log.warn("Step-up factor PASSWORD is configured. Passwordless (passkey-only and social-login) accounts "
                    + "cannot satisfy it, so step-up will not apply to them. Consider your account types carefully.");
        }
    }

    private static String knownFactors() {
        return new TreeSet<>(StepUpConfigProperties.FACTOR_AUTHORITIES.keySet()).toString();
    }
}
