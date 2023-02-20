package com.digitalsanctuary.spring.user.service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import com.digitalsanctuary.spring.user.event.AuditEvent;
import com.digitalsanctuary.spring.user.mail.MailService;
import com.digitalsanctuary.spring.user.persistence.model.PasswordResetToken;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.repository.PasswordResetTokenRepository;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class UserEmailService {

    /** The mail service. */
    @Autowired
    private MailService mailService;

    @Autowired
    private UserVerificationService userVerificationService;

    @Autowired
    private PasswordResetTokenRepository passwordTokenRepository;


    @Autowired
    private ApplicationEventPublisher eventPublisher;

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

        AuditEvent sendForgotPasswordEmailAuditEvent =
                new AuditEvent(this, user, "", "", "", "sendForgotPasswordVerificationEmail", "Success", "Forgot password email to be sent.", null);
        eventPublisher.publishEvent(sendForgotPasswordEmailAuditEvent);

        Map<String, Object> variables = new HashMap<String, Object>();
        variables.put("token", token);
        variables.put("appUrl", appUrl);
        variables.put("confirmationUrl", appUrl + "/user/changePassword?token=" + token);
        variables.put("user", user);

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

        Map<String, Object> variables = createEmailVariables(user, appUrl, token);

        mailService.sendTemplateMessage(user.getEmail(), "Registration Confirmation", variables, "mail/registration-token.html");
    }

    private Map<String, Object> createEmailVariables(final User user, final String appUrl, final String token) {
        Map<String, Object> variables = new HashMap<String, Object>();
        variables.put("token", token);
        variables.put("appUrl", appUrl);
        variables.put("confirmationUrl", appUrl + "/user/registrationConfirm?token=" + token);
        variables.put("user", user);
        return variables;
    }

    private String generateToken() {
        return UUID.randomUUID().toString();
    }

    public void createPasswordResetTokenForUser(final User user, final String token) {
        final PasswordResetToken myToken = new PasswordResetToken(token, user);
        passwordTokenRepository.save(myToken);
    }

}
