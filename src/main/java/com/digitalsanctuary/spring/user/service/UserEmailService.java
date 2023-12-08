package com.digitalsanctuary.spring.user.service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import com.digitalsanctuary.spring.user.event.AuditEvent;
import com.digitalsanctuary.spring.user.mail.MailService;
import com.digitalsanctuary.spring.user.persistence.model.PasswordResetToken;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.repository.PasswordResetTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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

}
