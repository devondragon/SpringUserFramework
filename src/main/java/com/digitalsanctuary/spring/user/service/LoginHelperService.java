package com.digitalsanctuary.spring.user.service;

import java.util.Collection;
import java.util.Date;
import java.util.Map;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.digitalsanctuary.spring.user.persistence.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Helper service for post-authentication user processing.
 *
 * <p>Provides common functionality used by {@link DSUserDetailsService} and {@link DSOAuth2UserService}
 * after a user has been authenticated, including updating activity timestamps, checking account
 * lockout status, and constructing the {@link DSUserDetails} object.</p>
 *
 * @see DSUserDetailsService
 * @see DSOAuth2UserService
 * @see DSUserDetails
 */
@Slf4j
@RequiredArgsConstructor
@Service
@Transactional
public class LoginHelperService {

    /** The login attempt service. */
    private final LoginAttemptService loginAttemptService;

    private final AuthorityService authorityService;

    /**
     * Helper method to authenticate a user after login. This method is called from the DSUserDetailsService after a user has been successfully
     * authenticated via local/password login. Attributes are populated from the {@link User} entity as a fallback.
     *
     * @param dbUser The user to authenticate.
     * @return The user details object.
     */
    public DSUserDetails userLoginHelper(User dbUser) {
        return userLoginHelper(dbUser, (Map<String, Object>) null);
    }

    /**
     * Helper method to authenticate a user after OAuth2 login, preserving the original provider attributes so that
     * {@link DSUserDetails#getAttributes()} returns the full set of attributes from the OAuth2 provider.
     *
     * @param dbUser     The user to authenticate.
     * @param attributes The OAuth2 provider attributes (may be null for local login fallback).
     * @return The user details object with provider attributes set.
     */
    public DSUserDetails userLoginHelper(User dbUser, Map<String, Object> attributes) {
        // Updating lastActivity date for this login
        dbUser.setLastActivityDate(new Date());

        // Check if the user account is locked, but should be unlocked now, and unlock it
        dbUser = loginAttemptService.checkIfUserShouldBeUnlocked(dbUser);

        // Enforce account status for all authentication paths (form, OAuth2, OIDC, WebAuthn)
        assertAccountUsable(dbUser);

        Collection<? extends GrantedAuthority> authorities = authorityService.getAuthoritiesFromUser(dbUser);
        return new DSUserDetails(dbUser, authorities, attributes);
    }

    /**
     * Helper method to authenticate an OIDC user after login, attaching the OIDC-specific tokens
     * and claims to the principal while keeping {@link DSUserDetails} immutable. Attributes are
     * populated from the OIDC ID token claims as a fallback.
     *
     * @param dbUser       The user to authenticate.
     * @param oidcUserInfo The OIDC user info claims.
     * @param oidcIdToken  The OIDC ID token.
     * @return The user details object with OIDC tokens set.
     */
    public DSUserDetails userLoginHelper(User dbUser, OidcUserInfo oidcUserInfo, OidcIdToken oidcIdToken) {
        return userLoginHelper(dbUser, oidcUserInfo, oidcIdToken, null);
    }

    /**
     * Helper method to authenticate an OIDC user after login, preserving the original provider attributes and
     * attaching the OIDC-specific tokens and claims to the principal.
     *
     * @param dbUser       The user to authenticate.
     * @param oidcUserInfo The OIDC user info claims.
     * @param oidcIdToken  The OIDC ID token.
     * @param attributes   The OIDC provider attributes (may be null to fall back to idToken claims).
     * @return The user details object with OIDC tokens and provider attributes set.
     */
    public DSUserDetails userLoginHelper(User dbUser, OidcUserInfo oidcUserInfo, OidcIdToken oidcIdToken,
            Map<String, Object> attributes) {
        // Updating lastActivity date for this login
        dbUser.setLastActivityDate(new Date());

        // Check if the user account is locked, but should be unlocked now, and unlock it
        dbUser = loginAttemptService.checkIfUserShouldBeUnlocked(dbUser);

        // Enforce account status for all authentication paths (form, OAuth2, OIDC, WebAuthn)
        assertAccountUsable(dbUser);

        Collection<? extends GrantedAuthority> authorities = authorityService.getAuthoritiesFromUser(dbUser);
        return new DSUserDetails(dbUser, oidcUserInfo, oidcIdToken, authorities, attributes);
    }

    /**
     * Verifies that the given user account is in a usable state for authentication. This enforces account status
     * ({@code locked}/{@code enabled}) for every authentication path that flows through this helper, including
     * OAuth2, OIDC, and WebAuthn (which load the user via {@link DSUserDetailsService}). Locked status is checked
     * before disabled status so a locked account surfaces a {@link LockedException} even if it is also disabled.
     *
     * @param user the user to validate (after any auto-unlock has been applied)
     * @throws LockedException   if the account is locked
     * @throws DisabledException if the account is disabled
     */
    private void assertAccountUsable(User user) {
        // Exception messages are intentionally generic (no PII): they can surface to WARN/ERROR logs and
        // user-facing error flows via handlers we do not control. The email is captured only in DEBUG logs.
        if (user.isLocked()) {
            log.debug("Rejecting authentication for locked account: {}", user.getEmail());
            throw new LockedException("Account is locked");
        }
        if (!user.isEnabled()) {
            log.debug("Rejecting authentication for disabled account: {}", user.getEmail());
            throw new DisabledException("Account is disabled");
        }
    }
}
