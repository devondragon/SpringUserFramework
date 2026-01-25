package com.digitalsanctuary.spring.user.service;

import java.util.Collection;
import java.util.Date;
import org.springframework.security.core.GrantedAuthority;
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
        dbUser.setLastActivityDate(new Date());

        // Check if the user account is locked, but should be unlocked now, and unlock it
        dbUser = loginAttemptService.checkIfUserShouldBeUnlocked(dbUser);

        Collection<? extends GrantedAuthority> authorities = authorityService.getAuthoritiesFromUser(dbUser);
        DSUserDetails userDetails = new DSUserDetails(dbUser, authorities);
        return userDetails;
    }
}
