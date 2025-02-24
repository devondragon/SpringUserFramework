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
 * The LoginHelperService class provides helper methods for authenticating users after login. This class is used by the DSUserDetailsService and
 * DSOAuth2UserService classes to authenticate users after they have been successfully authenticated.
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
