package com.digitalsanctuary.spring.user.service;

import java.util.Collection;
import java.util.Date;
import java.util.Map;
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

        Collection<? extends GrantedAuthority> authorities = authorityService.getAuthoritiesFromUser(dbUser);
        return new DSUserDetails(dbUser, oidcUserInfo, oidcIdToken, authorities, attributes);
    }
}
