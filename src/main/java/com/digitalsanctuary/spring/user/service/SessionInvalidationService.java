package com.digitalsanctuary.spring.user.service;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Service;
import com.digitalsanctuary.spring.user.persistence.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for invalidating user sessions.
 *
 * <p>Provides functionality to invalidate all active sessions for a given user, useful for
 * admin-initiated password resets and other security operations that require forcing users
 * to re-authenticate.</p>
 *
 * <p><strong>Race Condition Note:</strong> This service uses Spring's SessionRegistry to track
 * and invalidate sessions. Due to the nature of the SessionRegistry API, there is an inherent
 * race condition: sessions created after {@link SessionRegistry#getAllPrincipals()} is called
 * but before {@link SessionInformation#expireNow()} completes will not be invalidated. This is
 * a known limitation of the SessionRegistry approach. For most use cases (admin password reset),
 * this is acceptable as the window is very small.</p>
 *
 * @author Devon Hillard
 * @see SessionRegistry
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class SessionInvalidationService {

    private final SessionRegistry sessionRegistry;

    /** Threshold for warning about high principal count that may impact performance. */
    @Value("${user.session.invalidation.warn-threshold:1000}")
    private int warnThreshold;

    /**
     * Invalidates all active sessions for the given user.
     * This forces the user to re-authenticate on their next request.
     *
     * <p><strong>Note:</strong> Sessions created after this method starts iterating
     * but before it completes will not be invalidated. This race condition is inherent
     * to the SessionRegistry API and is acceptable for most security operations.</p>
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

        // Performance monitoring: warn if principal count is high
        if (principals.size() > warnThreshold) {
            log.warn("SessionInvalidationService.invalidateUserSessions: high principal count ({}) may impact performance",
                    principals.size());
        }

        log.debug("SessionInvalidationService.invalidateUserSessions: scanning {} principals for user {}",
                principals.size(), user.getEmail());

        // NOTE: Sessions created after getAllPrincipals() but before expireNow()
        // will not be invalidated. This is a known limitation of SessionRegistry.
        for (Object principal : principals) {
            User principalUser = extractUser(principal);

            if (principalUser != null && principalUser.getId().equals(user.getId())) {
                List<SessionInformation> sessions = sessionRegistry.getAllSessions(principal, false);
                for (SessionInformation session : sessions) {
                    session.expireNow();
                    invalidatedCount++;
                    // Log truncated session ID to avoid exposing full session identifiers
                    String sessionId = session.getSessionId();
                    String safeSessionId = sessionId.length() > 8 ? sessionId.substring(0, 8) + "..." : sessionId;
                    log.debug("SessionInvalidationService.invalidateUserSessions: expired session {} for user {}",
                            safeSessionId, user.getEmail());
                }
            }
        }

        log.info("SessionInvalidationService.invalidateUserSessions: invalidated {} sessions for user {} (scanned {} principals)",
                invalidatedCount, user.getEmail(), principals.size());
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
