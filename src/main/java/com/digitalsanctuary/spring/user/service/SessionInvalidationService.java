package com.digitalsanctuary.spring.user.service;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.digitalsanctuary.spring.user.persistence.model.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for invalidating user sessions.
 *
 * <p>Provides functionality to invalidate active sessions for a given user, useful for
 * admin-initiated password resets and other security operations that require forcing users
 * to re-authenticate. {@link #invalidateUserSessions(User)} terminates <em>every</em> session for the user;
 * {@link #invalidateSessionsAfterPasswordChange(User)} applies the self-service password-change policy, which by
 * default preserves and regenerates the user's current session while invalidating their other sessions.</p>
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
@Service("dsSessionInvalidationService")
public class SessionInvalidationService {

    private final SessionRegistry sessionRegistry;

    /** Threshold for warning about high principal count that may impact performance. */
    @Value("${user.session.invalidation.warn-threshold:1000}")
    private int warnThreshold;

    /**
     * When {@code true} (the default), a self-service password change preserves the user's <em>current</em> session
     * (regenerating its id to mitigate session fixation) and invalidates only the user's <em>other</em> sessions, so
     * the user stays logged in after changing their own password. When {@code false}, every session for the user is
     * invalidated, including the current one (the pre-4.x behavior), forcing an immediate re-login.
     */
    @Value("${user.session.invalidation.keep-current-session-on-password-change:true}")
    private boolean keepCurrentSessionOnPasswordChange;

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
                    log.debug("SessionInvalidationService.invalidateUserSessions: expired session {} for user {}",
                            truncateSessionId(session.getSessionId()), user.getEmail());
                }
            }
        }

        log.info("SessionInvalidationService.invalidateUserSessions: invalidated {} sessions for user {} (scanned {} principals)",
                invalidatedCount, user.getEmail(), principals.size());
        return invalidatedCount;
    }

    /**
     * Invalidates sessions after a <em>self-service</em> password change, applying the configured policy
     * ({@code user.session.invalidation.keep-current-session-on-password-change}, default {@code true}).
     *
     * <p>
     * With the default policy, the user's <em>current</em> session is preserved and its id is regenerated (mitigating
     * session fixation), while every <em>other</em> session for the user is invalidated &mdash; so the user remains
     * logged in on the device they just used to change their password, but any other active sessions are terminated.
     * This follows the OWASP guidance to regenerate the current session and invalidate the rest on a credential change.
     * </p>
     *
     * <p>
     * When the policy is disabled, this delegates to {@link #invalidateUserSessions(User)} and terminates <em>all</em>
     * sessions including the current one. If there is no current servlet request/session (e.g. the password is being
     * changed through a flow where the user is not authenticated in a session, such as a token-based password reset),
     * there is no current session to preserve, so all of the user's registered sessions are invalidated.
     * </p>
     *
     * @param user the user whose sessions should be invalidated
     * @return the number of <em>other</em> sessions that were invalidated (the preserved current session is not counted)
     */
    public int invalidateSessionsAfterPasswordChange(User user) {
        if (!keepCurrentSessionOnPasswordChange) {
            return invalidateUserSessions(user);
        }
        if (user == null) {
            log.warn("SessionInvalidationService.invalidateSessionsAfterPasswordChange: user is null");
            return 0;
        }

        final HttpServletRequest request = currentRequest();
        final String currentSessionId = currentSessionId(request);

        int invalidatedCount = 0;
        Object currentPrincipal = null;
        final List<Object> principals = sessionRegistry.getAllPrincipals();
        if (principals.size() > warnThreshold) {
            log.warn("SessionInvalidationService.invalidateSessionsAfterPasswordChange: high principal count ({}) may impact performance",
                    principals.size());
        }

        for (Object principal : principals) {
            User principalUser = extractUser(principal);
            if (principalUser != null && principalUser.getId().equals(user.getId())) {
                for (SessionInformation session : sessionRegistry.getAllSessions(principal, false)) {
                    if (currentSessionId != null && currentSessionId.equals(session.getSessionId())) {
                        // Preserve the current session; it is regenerated below rather than expired.
                        currentPrincipal = principal;
                        continue;
                    }
                    session.expireNow();
                    invalidatedCount++;
                    log.debug("SessionInvalidationService.invalidateSessionsAfterPasswordChange: expired other session {} for user {}",
                            truncateSessionId(session.getSessionId()), user.getEmail());
                }
            }
        }

        if (currentPrincipal != null) {
            regenerateCurrentSession(request, currentSessionId, currentPrincipal, user);
        }

        log.info("SessionInvalidationService.invalidateSessionsAfterPasswordChange: invalidated {} other session(s) for user {}; "
                + "current session preserved and regenerated: {}", invalidatedCount, user.getEmail(), currentPrincipal != null);
        return invalidatedCount;
    }

    /**
     * Regenerates the current HTTP session id (preserving the session and its {@code SecurityContext}) and keeps the
     * {@link SessionRegistry} consistent so the concurrent-session machinery recognizes the new id on the next request.
     * Best-effort: if there is no active session to regenerate (e.g. it was already invalidated or the response is
     * committed), the user simply keeps their existing session id.
     *
     * @param request the current servlet request (non-null)
     * @param oldSessionId the current session id prior to regeneration
     * @param principal the security principal the session is registered under
     * @param user the user (for logging)
     */
    private void regenerateCurrentSession(HttpServletRequest request, String oldSessionId, Object principal, User user) {
        try {
            final String newSessionId = request.changeSessionId();
            if (!newSessionId.equals(oldSessionId)) {
                sessionRegistry.removeSessionInformation(oldSessionId);
                sessionRegistry.registerNewSession(newSessionId, principal);
                log.debug("SessionInvalidationService.regenerateCurrentSession: regenerated current session {} -> {} for user {}",
                        truncateSessionId(oldSessionId), truncateSessionId(newSessionId), user.getEmail());
            }
        } catch (IllegalStateException ex) {
            log.debug("SessionInvalidationService.regenerateCurrentSession: could not regenerate current session for user {}: {}",
                    user.getEmail(), ex.getMessage());
        }
    }

    /**
     * Returns the current servlet request bound to this thread, or {@code null} if the call is not happening on a
     * request-bound thread (e.g. a background job).
     *
     * @return the current {@link HttpServletRequest}, or {@code null}
     */
    private HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest();
        }
        return null;
    }

    /**
     * Returns the id of the existing session on the given request, or {@code null} if the request is {@code null} or
     * has no session.
     *
     * @param request the current request (may be {@code null})
     * @return the current session id, or {@code null}
     */
    private String currentSessionId(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        HttpSession session = request.getSession(false);
        return session != null ? session.getId() : null;
    }

    /**
     * Truncates a session id for safe logging, never exposing the full identifier.
     *
     * @param sessionId the full session id
     * @return the first 8 characters followed by an ellipsis, or the id unchanged if it is short
     */
    private String truncateSessionId(String sessionId) {
        return sessionId != null && sessionId.length() > 8 ? sessionId.substring(0, 8) + "..." : sessionId;
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
