package com.digitalsanctuary.spring.user.security;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import org.springframework.security.authorization.AllRequiredFactorsAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.authorization.RequiredFactor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import com.digitalsanctuary.spring.user.persistence.model.User;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * The framework's built-in {@link StepUpService}: step-up is satisfied when the current authentication carries a
 * configured factor that was issued within {@code user.security.stepUp.ttlSeconds}.
 *
 * <p>
 * Spring Security stamps a {@code FactorGrantedAuthority} on every successful login and records when it was issued
 * (for passkeys, {@code WebAuthnAuthenticationProvider} adds {@code FACTOR_WEBAUTHN} with the moment of assertion).
 * Re-running that login while already authenticated merges the new factor into the existing session, so its issue time
 * moves forward and the user's other authorities survive. Step-up is therefore just "assert again", using the login
 * ceremony the application already has: no challenge endpoint, no server-side challenge state, no step-up token.
 * </p>
 *
 * <p>
 * That merge is not on by default. It requires {@code mfaEnabled} on the authentication filters, which
 * {@link MfaFilterMergingConfiguration} sets whenever MFA <em>or</em> step-up is enabled. Without it a second login
 * replaces the first, and the user would lose every authority the {@code UserDetailsService} does not re-supply.
 * </p>
 *
 * <h2>What this does and does not bind</h2>
 *
 * <p>
 * The proof is bound to the user (the factor lives on their authentication), to the session (authentication is
 * session-scoped), and to time (the TTL). It is <strong>not</strong> single-use and <strong>not</strong> bound to a
 * particular operation: within the window, one ceremony authorizes any credential-altering operation on that session,
 * and {@code action} is used only for logging.
 *
 * <p>
 * This closes the case the feature exists for, an attacker holding a session cookie but no authenticator. That
 * depends on passkey <em>enrollment</em> being gated too, which {@code user.security.stepUp.enrollmentTtlSeconds}
 * does: otherwise such an attacker would simply register their own authenticator and assert with it, producing a
 * genuinely fresh factor. Two narrower cases stay open: an attacker sharing the session concurrently can piggyback
 * inside either window, and a session stolen within {@code enrollmentTtlSeconds} of a real login can still enroll.
 * Keep both TTLs short.
 * </p>
 *
 * <p>
 * Note for anyone tempted to make the proof single-use: do not consume it by removing the factor authority. When
 * {@code user.mfa.factors} includes the same factor, removing it revokes access to every authenticated endpoint,
 * because MFA enforcement reads that authority too. Re-stamp it with a backdated issue time instead, since MFA checks
 * only presence while step-up checks age.
 * </p>
 */
@Slf4j
public class DSFactorFreshnessStepUpService implements StepUpService {

    private final List<AuthorizationManager<Object>> factorManagers;
    private final List<String> factorNames;
    private final Duration ttl;
    private final Predicate<User> hasPasskey;

    private SecurityContextHolderStrategy securityContextHolderStrategy = SecurityContextHolder.getContextHolderStrategy();

    /**
     * Creates a service enforcing the configured factors and TTL.
     *
     * @param config the step-up configuration; its factor names must already have been validated
     * @param hasPasskey answers whether a user holds at least one registered WebAuthn credential, used by
     *        {@link #canSatisfyStepUp(User, String)} to tell "has not asserted" apart from "has nothing to assert with"
     */
    public DSFactorFreshnessStepUpService(StepUpConfigProperties config, Predicate<User> hasPasskey) {
        this.hasPasskey = hasPasskey;
        this.ttl = Duration.ofSeconds(config.getTtlSeconds());
        this.factorNames = List.copyOf(config.getFactors());
        // One manager per factor, evaluated as "any of". Spring Security 7.1 offers
        // AllRequiredFactorsAuthorizationManager.anyOf for this, but it is deliberately not used: the framework also
        // supports Spring Security 7.0 consumers, where that method does not exist.
        this.factorManagers = this.factorNames.stream().map(name -> buildManager(name, this.ttl)).toList();
    }

    private static AuthorizationManager<Object> buildManager(String factorName, Duration ttl) {
        String authority = StepUpConfigProperties.FACTOR_AUTHORITIES.get(factorName.toUpperCase(Locale.ROOT));
        return AllRequiredFactorsAuthorizationManager.<Object>builder()
                .requireFactor(RequiredFactor.withAuthority(authority).validDuration(ttl).build()).build();
    }

    /**
     * Sets the {@link SecurityContextHolderStrategy} used to read the current authentication.
     *
     * @param securityContextHolderStrategy the strategy to use; must not be null
     */
    public void setSecurityContextHolderStrategy(SecurityContextHolderStrategy securityContextHolderStrategy) {
        this.securityContextHolderStrategy = securityContextHolderStrategy;
    }

    @Override
    public boolean isStepUpSatisfied(User user, String action, HttpServletRequest request) {
        Authentication authentication = securityContextHolderStrategy.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            log.debug("Step-up denied for action {}: no authenticated request context", action);
            return false;
        }
        if (user == null || user.getEmail() == null || !user.getEmail().equalsIgnoreCase(authentication.getName())) {
            // The operation targets someone other than the authenticated caller, so the caller's factor says nothing
            // about it. Callers in this framework always pass the authenticated user, so this indicates a bug or a
            // consumer calling the SPI directly.
            log.warn("Step-up denied for action {}: target user does not match the authenticated principal", action);
            return false;
        }

        for (AuthorizationManager<Object> manager : factorManagers) {
            AuthorizationResult result = manager.authorize(() -> authentication, action);
            if (result != null && result.isGranted()) {
                log.debug("Step-up satisfied for action {} by a factor issued within {}", action, ttl);
                return true;
            }
        }

        log.debug("Step-up denied for action {}: no factor of {} issued within {}", action, factorNames, ttl);
        return false;
    }

    /**
     * Reports whether any configured factor is achievable for this user at all.
     *
     * <p>
     * {@code WEBAUTHN} needs a registered passkey and {@code PASSWORD} needs a password, so an account holding
     * neither cannot produce either no matter what the user does. Every other factor is delivered out of band and
     * cannot be ruled out from here, so it is assumed achievable and the operation stays gated.
     * </p>
     *
     * @param user the authenticated user the operation targets
     * @param action the operation being gated, used only for logging
     * @return {@code true} if at least one configured factor could be produced by this user
     */
    @Override
    public boolean canSatisfyStepUp(User user, String action) {
        if (user == null) {
            // Contract violation rather than a real account. Keep the gate; isStepUpSatisfied does the denying.
            return true;
        }
        for (String factorName : factorNames) {
            switch (factorName.toUpperCase(Locale.ROOT)) {
                case "WEBAUTHN" -> {
                    if (hasPasskey.test(user)) {
                        return true;
                    }
                }
                case "PASSWORD" -> {
                    if (user.getPassword() != null && !user.getPassword().isEmpty()) {
                        return true;
                    }
                }
                default -> {
                    return true;
                }
            }
        }
        log.debug("Step-up does not apply to action {}: the account holds no credential able to produce any of {}",
                action, factorNames);
        return false;
    }
}
