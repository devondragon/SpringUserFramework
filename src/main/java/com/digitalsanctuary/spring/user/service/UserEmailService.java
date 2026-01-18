package com.digitalsanctuary.spring.user.service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
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
     * @param user the user to reset password for
     * @param appUrl the application URL for the reset link
     * @param adminIdentifier identifier of the admin initiating the reset (e.g., email)
     * @param invalidateSessions whether to invalidate all user sessions
     * @return the number of sessions invalidated (0 if invalidateSessions is false)
     */
    public int initiateAdminPasswordReset(final User user, final String appUrl, final String adminIdentifier,
            final boolean invalidateSessions) {
        log.debug("UserEmailService.initiateAdminPasswordReset: called for user: {} by admin: {}", user.getEmail(),
                adminIdentifier);

        int invalidatedSessions = 0;

        // Step 1: Optionally invalidate all user sessions
        if (invalidateSessions) {
            invalidatedSessions = sessionInvalidationService.invalidateUserSessions(user);
            log.info("UserEmailService.initiateAdminPasswordReset: invalidated {} sessions for user {}",
                    invalidatedSessions, user.getEmail());
        }

        // Step 2: Generate token and create password reset token
        final String token = generateToken();
        createPasswordResetTokenForUser(user, token);

        // Step 3: Publish admin-specific audit event
        String auditMessage = String.format("Admin-initiated password reset by %s. Sessions invalidated: %d",
                adminIdentifier, invalidatedSessions);
        AuditEvent adminPasswordResetAuditEvent = AuditEvent.builder()
                .source(this)
                .user(user)
                .action("adminInitiatedPasswordReset")
                .actionStatus("Success")
                .message(auditMessage)
                .extraData("adminIdentifier:" + adminIdentifier + ",sessionsInvalidated:" + invalidatedSessions)
                .build();

        eventPublisher.publishEvent(adminPasswordResetAuditEvent);

        // Step 4: Send password reset email
        Map<String, Object> variables = createEmailVariables(user, appUrl, token, "/user/changePassword?token=");
        mailService.sendTemplateMessage(user.getEmail(), "Password Reset", variables, "mail/forgot-password-token.html");

        log.info("UserEmailService.initiateAdminPasswordReset: password reset email sent to {} by admin {}",
                user.getEmail(), adminIdentifier);

        return invalidatedSessions;
    }

    /**
     * Convenience overload that uses the configured appUrl and invalidates sessions by default.
     *
     * @param user the user to reset password for
     * @param adminIdentifier identifier of the admin initiating the reset (e.g., email)
     * @return the number of sessions invalidated
     * @throws IllegalStateException if user.admin.appUrl is not configured
     */
    public int initiateAdminPasswordReset(final User user, final String adminIdentifier) {
        if (configuredAppUrl == null || configuredAppUrl.isBlank()) {
            throw new IllegalStateException(
                    "user.admin.appUrl must be configured to use initiateAdminPasswordReset without explicit appUrl");
        }
        return initiateAdminPasswordReset(user, configuredAppUrl, adminIdentifier, true);
    }

}
