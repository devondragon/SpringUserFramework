package com.digitalsanctuary.spring.user.service;

import java.time.Instant;
import java.util.Collection;
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
     * Helper method to authenticate a user after login. This method is called from the DSUserDetailsService and DSOAuth2UserService classes after a
     * user has been successfully authenticated.
     *
     * @param dbUser The user to authenticate.
     * @return The user details object.
     */
    public DSUserDetails userLoginHelper(User dbUser) {
        // Updating lastActivity date for this login
        dbUser.setLastActivityDate(Instant.now());

        // Check if the user account is locked, but should be unlocked now, and unlock it
        dbUser = loginAttemptService.checkIfUserShouldBeUnlocked(dbUser);

        Collection<? extends GrantedAuthority> authorities = authorityService.getAuthoritiesFromUser(dbUser);
        DSUserDetails userDetails = new DSUserDetails(dbUser, authorities);
        return userDetails;
    }

    /**
     * Helper method to authenticate an OIDC user after login, attaching the OIDC-specific tokens
     * and claims to the principal while keeping {@link DSUserDetails} immutable.
     *
     * @param dbUser       The user to authenticate.
     * @param oidcUserInfo The OIDC user info claims.
     * @param oidcIdToken  The OIDC ID token.
     * @return The user details object with OIDC tokens set.
     */
    public DSUserDetails userLoginHelper(User dbUser, OidcUserInfo oidcUserInfo, OidcIdToken oidcIdToken) {
        // Updating lastActivity date for this login
        dbUser.setLastActivityDate(Instant.now());

        // Check if the user account is locked, but should be unlocked now, and unlock it
        dbUser = loginAttemptService.checkIfUserShouldBeUnlocked(dbUser);

        Collection<? extends GrantedAuthority> authorities = authorityService.getAuthoritiesFromUser(dbUser);
        return new DSUserDetails(dbUser, oidcUserInfo, oidcIdToken, authorities);
    }
}
