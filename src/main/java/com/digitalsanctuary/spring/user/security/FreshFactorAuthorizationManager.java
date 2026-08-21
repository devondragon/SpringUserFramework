package com.digitalsanctuary.spring.user.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import lombok.extern.slf4j.Slf4j;

/**
 * Grants access only when the caller authenticated recently, by any means.
 *
 * <p>
 * Used to gate passkey enrollment, the way GitHub's sudo mode asks for a password before adding a security key.
 * Enrolling a credential is what makes a stolen session durable: the new passkey outlives a password change, and it
 * refreshes {@code FACTOR_WEBAUTHN}, which would otherwise let an attacker satisfy step-up with an authenticator
 * they enrolled moments earlier.
 * </p>
 *
 * <p>
 * Any {@link FactorGrantedAuthority} counts, deliberately not the {@code user.security.stepUp.factors} list. With the
 * default {@code [WEBAUTHN]}, requiring a configured factor would demand a passkey in order to register a first
 * passkey, which no one could satisfy. A plain authority merely named like a factor carries no issue time and can
 * never be fresh, so it cannot be used to forge recency.
 * </p>
 *
 * @param <T> the authorization context type, unused: the decision depends only on the authentication
 */
@Slf4j
public class FreshFactorAuthorizationManager<T> implements AuthorizationManager<T> {

    private final Duration ttl;
    private final Clock clock;

    /**
     * Creates a manager granting access when a factor was issued within the given window.
     *
     * @param ttl how recently the caller must have authenticated
     * @param clock the time source, injectable so freshness boundaries can be tested deterministically
     */
    public FreshFactorAuthorizationManager(Duration ttl, Clock clock) {
        this.ttl = ttl;
        this.clock = clock;
    }

    @Override
    public AuthorizationResult authorize(Supplier<? extends Authentication> authentication, T context) {
        Authentication auth = authentication != null ? authentication.get() : null;
        if (auth == null || !auth.isAuthenticated()) {
            return new AuthorizationDecision(false);
        }

        Instant cutoff = clock.instant().minus(ttl);
        boolean fresh = auth.getAuthorities().stream().filter(FactorGrantedAuthority.class::isInstance)
                .map(FactorGrantedAuthority.class::cast).map(FactorGrantedAuthority::getIssuedAt)
                .anyMatch(issuedAt -> issuedAt != null && issuedAt.isAfter(cutoff));

        if (!fresh) {
            log.debug("Passkey enrollment denied for {}: no authentication factor issued within {}", auth.getName(), ttl);
        }
        return new AuthorizationDecision(fresh);
    }
}
