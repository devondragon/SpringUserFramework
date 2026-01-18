package com.digitalsanctuary.spring.user.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import com.digitalsanctuary.spring.user.audit.AuditEvent;
import com.digitalsanctuary.spring.user.mail.MailService;
import com.digitalsanctuary.spring.user.persistence.model.PasswordResetToken;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.repository.PasswordResetTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The UserEmailService class provides methods for sending emails to users for various purposes, such as registration verification and password reset.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class UserEmailService {

    /** The mail service. */
    private final MailService mailService;

    /** The user verification service. */
    private final UserVerificationService userVerificationService;

    /** The password token repository. */
    private final PasswordResetTokenRepository passwordTokenRepository;

    /** The event publisher. */
    private final ApplicationEventPublisher eventPublisher;

    /** The session invalidation service. */
    private final SessionInvalidationService sessionInvalidationService;

    /** The configured app URL for admin-initiated password resets. */
    @Value("${user.admin.appUrl:#{null}}")
    private String configuredAppUrl;

    /**
     * Send forgot password verification email.
     *
     * @param user the user
     * @param appUrl the app url
     */
    public void sendForgotPasswordVerificationEmail(final User user, final String appUrl) {
        log.debug("UserEmailService.sendForgotPasswordVerificationEmail: called with user: {}", user);
        final String token = generateToken();
        createPasswordResetTokenForUser(user, token);

        AuditEvent sendForgotPasswordEmailAuditEvent = AuditEvent.builder().source(this).user(user).action("sendForgotPasswordVerificationEmail")
                .actionStatus("Success").message("Forgot password email to be sent.").build();

        eventPublisher.publishEvent(sendForgotPasswordEmailAuditEvent);

        Map<String, Object> variables = createEmailVariables(user, appUrl, token, "/user/changePassword?token=");

        mailService.sendTemplateMessage(user.getEmail(), "Password Reset", variables, "mail/forgot-password-token.html");
    }

    /**
     * Handle the completed registration.
     *
     * Create a Verification token for the user, and send the email out.
     *
     * @param user the user
     * @param appUrl the app url
     */
    public void sendRegistrationVerificationEmail(final User user, final String appUrl) {
        final String token = generateToken();
        userVerificationService.createVerificationTokenForUser(user, token);

        Map<String, Object> variables = createEmailVariables(user, appUrl, token, "/user/registrationConfirm?token=");

        mailService.sendTemplateMessage(user.getEmail(), "Registration Confirmation", variables, "mail/registration-token.html");
    }

    /**
     * Creates the email variables.
     *
     * @param user the user
     * @param appUrl the app url
     * @param token the token
     * @param confirmationPath the confirmation path
     * @return the map
     * @throws IllegalArgumentException if appUrl is invalid (for admin-initiated resets)
     */
    private Map<String, Object> createEmailVariables(final User user, final String appUrl, final String token, final String confirmationPath) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("token", token);
        variables.put("appUrl", appUrl);
        variables.put("confirmationUrl", appUrl + confirmationPath + token);
        variables.put("user", user);
        return variables;
    }

    /**
     * Creates the email variables with URL validation.
     * This is used for admin-initiated password resets where URL validation is critical.
     *
     * @param user the user
     * @param appUrl the app url
     * @param token the token
     * @param confirmationPath the confirmation path
     * @return the map
     * @throws IllegalArgumentException if appUrl is invalid
     */
    private Map<String, Object> createEmailVariablesWithValidation(final User user, final String appUrl, final String token,
            final String confirmationPath) {
        if (!isValidAppUrl(appUrl)) {
            throw new IllegalArgumentException("Invalid application URL: " + appUrl);
        }
        return createEmailVariables(user, appUrl, token, confirmationPath);
    }

    /**
     * Validates that the given URL is a valid HTTP/HTTPS URL.
     * Rejects dangerous URL schemes like javascript:, data:, etc.
     *
     * @param url the URL to validate
     * @return true if the URL is valid and safe, false otherwise
     */
    private boolean isValidAppUrl(final String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            return scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                    && uri.getHost() != null;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /**
     * Gets the current admin's identifier from the SecurityContext.
     * This is used for audit logging to ensure the admin identity is derived
     * from the authenticated principal rather than user-supplied input.
     *
     * @return the admin identifier (email or username), or "UNKNOWN_ADMIN" if not authenticated
     */
    private String getCurrentAdminIdentifier() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return "UNKNOWN_ADMIN";
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof DSUserDetails details) {
            return details.getUser().getEmail();
        }
        return auth.getName();
    }

    /**
     * Generate random token.
     *
     * @return the string
     */
    private String generateToken() {
        return UUID.randomUUID().toString();
    }

    /**
     * Creates the password reset token for user.
     *
     * @param user the user
     * @param token the token
     */
    public void createPasswordResetTokenForUser(final User user, final String token) {
        final PasswordResetToken myToken = new PasswordResetToken(token, user);
        passwordTokenRepository.save(myToken);
    }

    /**
     * Initiates an admin-triggered password reset for a user.
     * This method:
     * 1. Optionally invalidates all active sessions for the user
     * 2. Generates a password reset token
     * 3. Sends the password reset email
     * 4. Publishes an audit event for tracking
     *
     * <p>Note: Email sending is asynchronous with retry. Delivery status is logged
     * but not returned. The audit event provides tracking for admin actions.</p>
     *
     * @param user the user to reset password for
     * @param appUrl the application URL for the reset link (must be valid HTTP/HTTPS URL)
     * @param invalidateSessions whether to invalidate all user sessions
     * @return the number of sessions invalidated (0 if invalidateSessions is false)
     * @throws IllegalArgumentException if appUrl is invalid
     */
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public int initiateAdminPasswordReset(final User user, final String appUrl, final boolean invalidateSessions) {
        final String correlationId = generateToken();
        final String adminIdentifier = getCurrentAdminIdentifier();

        log.debug("UserEmailService.initiateAdminPasswordReset: called for user: {} by admin: {} [correlationId={}]",
                user.getEmail(), adminIdentifier, correlationId);

        // Step 1: Optionally invalidate all user sessions
        int invalidatedSessions = handleSessionInvalidation(user, invalidateSessions, correlationId);

        // Step 2: Generate token and create password reset token
        final String token = generateToken();
        createPasswordResetTokenForUser(user, token);

        // Step 3: Publish admin-specific audit event
        publishAdminPasswordResetAuditEvent(user, adminIdentifier, invalidatedSessions, correlationId);

        // Step 4: Send password reset email
        sendPasswordResetEmail(user, appUrl, token);

        log.info("UserEmailService.initiateAdminPasswordReset: password reset email sent to {} by admin {} [correlationId={}]",
                user.getEmail(), adminIdentifier, correlationId);

        return invalidatedSessions;
    }

    /**
     * Convenience overload that uses the configured appUrl and invalidates sessions by default.
     *
     * <p>Note: Email sending is asynchronous with retry. Delivery status is logged
     * but not returned. The audit event provides tracking for admin actions.</p>
     *
     * @param user the user to reset password for
     * @return the number of sessions invalidated
     * @throws IllegalStateException if user.admin.appUrl is not configured
     */
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public int initiateAdminPasswordReset(final User user) {
        if (configuredAppUrl == null || configuredAppUrl.isBlank()) {
            throw new IllegalStateException(
                    "user.admin.appUrl must be configured to use initiateAdminPasswordReset without explicit appUrl");
        }
        return initiateAdminPasswordReset(user, configuredAppUrl, true);
    }

    /**
     * Initiates an admin-triggered password reset for a user.
     *
     * @param user the user to reset password for
     * @param appUrl the application URL for the reset link
     * @param adminIdentifier identifier of the admin initiating the reset (ignored, derived from SecurityContext)
     * @param invalidateSessions whether to invalidate all user sessions
     * @return the number of sessions invalidated (0 if invalidateSessions is false)
     * @deprecated Use {@link #initiateAdminPasswordReset(User, String, boolean)} instead.
     *             The adminIdentifier is now derived from the SecurityContext for security.
     */
    @Deprecated(forRemoval = true)
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public int initiateAdminPasswordReset(final User user, final String appUrl, final String adminIdentifier,
            final boolean invalidateSessions) {
        log.warn("UserEmailService.initiateAdminPasswordReset: adminIdentifier parameter is deprecated and ignored. "
                + "Admin identity is now derived from SecurityContext.");
        return initiateAdminPasswordReset(user, appUrl, invalidateSessions);
    }

    /**
     * Convenience overload that uses the configured appUrl and invalidates sessions by default.
     *
     * @param user the user to reset password for
     * @param adminIdentifier identifier of the admin initiating the reset (ignored, derived from SecurityContext)
     * @return the number of sessions invalidated
     * @throws IllegalStateException if user.admin.appUrl is not configured
     * @deprecated Use {@link #initiateAdminPasswordReset(User)} instead.
     *             The adminIdentifier is now derived from the SecurityContext for security.
     */
    @Deprecated(forRemoval = true)
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public int initiateAdminPasswordReset(final User user, final String adminIdentifier) {
        log.warn("UserEmailService.initiateAdminPasswordReset: adminIdentifier parameter is deprecated and ignored. "
                + "Admin identity is now derived from SecurityContext.");
        return initiateAdminPasswordReset(user);
    }

    /**
     * Handles optional session invalidation for admin password reset.
     *
     * @param user the user whose sessions may be invalidated
     * @param invalidateSessions whether to invalidate sessions
     * @param correlationId the correlation ID for tracking
     * @return the number of sessions invalidated
     */
    private int handleSessionInvalidation(final User user, final boolean invalidateSessions, final String correlationId) {
        if (!invalidateSessions) {
            return 0;
        }
        int invalidatedCount = sessionInvalidationService.invalidateUserSessions(user);
        log.info("UserEmailService.initiateAdminPasswordReset: invalidated {} sessions for user {} [correlationId={}]",
                invalidatedCount, user.getEmail(), correlationId);
        return invalidatedCount;
    }

    /**
     * Publishes an audit event for admin-initiated password reset.
     *
     * @param user the user whose password is being reset
     * @param adminIdentifier the admin's identifier
     * @param invalidatedSessions the number of sessions invalidated
     * @param correlationId the correlation ID for tracking
     */
    private void publishAdminPasswordResetAuditEvent(final User user, final String adminIdentifier,
            final int invalidatedSessions, final String correlationId) {
        String auditMessage = String.format("Admin-initiated password reset by %s. Sessions invalidated: %d",
                adminIdentifier, invalidatedSessions);
        AuditEvent adminPasswordResetAuditEvent = AuditEvent.builder()
                .source(this)
                .user(user)
                .action("adminInitiatedPasswordReset")
                .actionStatus("Success")
                .message(auditMessage)
                .extraData(String.format("adminIdentifier:%s,sessionsInvalidated:%d,correlationId:%s",
                        adminIdentifier, invalidatedSessions, correlationId))
                .build();

        eventPublisher.publishEvent(adminPasswordResetAuditEvent);
    }

    /**
     * Sends the password reset email to the user.
     *
     * @param user the user to send the email to
     * @param appUrl the application URL for the reset link
     * @param token the password reset token
     * @throws IllegalArgumentException if appUrl is invalid
     */
    private void sendPasswordResetEmail(final User user, final String appUrl, final String token) {
        Map<String, Object> variables = createEmailVariablesWithValidation(user, appUrl, token, "/user/changePassword?token=");
        mailService.sendTemplateMessage(user.getEmail(), "Password Reset", variables, "mail/forgot-password-token.html");
    }

}
