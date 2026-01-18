package com.digitalsanctuary.spring.user.service;

import java.util.List;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Service;
import com.digitalsanctuary.spring.user.persistence.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for invalidating user sessions. This is useful for admin-initiated password resets
 * and other security operations that require forcing users to re-authenticate.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class SessionInvalidationService {

    private final SessionRegistry sessionRegistry;

    /**
     * Invalidates all active sessions for the given user.
     * This forces the user to re-authenticate on their next request.
     *
     * @param user the user whose sessions should be invalidated
     * @return the number of sessions that were invalidated
     */
    public int invalidateUserSessions(User user) {
        if (user == null) {
            log.warn("SessionInvalidationService.invalidateUserSessions: user is null");
            return 0;
        }

        int invalidatedCount = 0;
        List<Object> principals = sessionRegistry.getAllPrincipals();

        for (Object principal : principals) {
            User principalUser = extractUser(principal);

            if (principalUser != null && principalUser.getId().equals(user.getId())) {
                List<SessionInformation> sessions = sessionRegistry.getAllSessions(principal, false);
                for (SessionInformation session : sessions) {
                    session.expireNow();
                    invalidatedCount++;
                    log.debug("SessionInvalidationService.invalidateUserSessions: expired session {} for user {}",
                            session.getSessionId(), user.getEmail());
                }
            }
        }

        log.info("SessionInvalidationService.invalidateUserSessions: invalidated {} sessions for user {}",
                invalidatedCount, user.getEmail());
        return invalidatedCount;
    }

    /**
     * Extracts the User object from a principal.
     * Handles both User and DSUserDetails principal types.
     *
     * @param principal the security principal
     * @return the User object, or null if not extractable
     */
    private User extractUser(Object principal) {
        if (principal instanceof User user) {
            return user;
        } else if (principal instanceof DSUserDetails dsUserDetails) {
            return dsUserDetails.getUser();
        }
        return null;
    }
}
