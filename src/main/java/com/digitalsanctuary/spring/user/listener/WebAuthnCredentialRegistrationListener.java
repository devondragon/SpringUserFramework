package com.digitalsanctuary.spring.user.listener;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import com.digitalsanctuary.spring.user.audit.AuditEvent;
import com.digitalsanctuary.spring.user.event.WebAuthnCredentialRegisteredEvent;
import com.digitalsanctuary.spring.user.security.WebAuthnConfigProperties;
import com.digitalsanctuary.spring.user.service.UserEmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Records and announces passkey enrollment.
 *
 * <p>
 * A newly enrolled credential is a durable new way into the account, and it outlives a password change, since
 * session invalidation ends sessions rather than credentials. An attacker who reaches an authenticated session can
 * therefore leave themselves a way back in. Preventing that is the job of step-up
 * ({@code user.security.stepUp.enabled}); this listener is the detective half, so the enrollment is at least
 * recorded and visible to the account owner whether or not step-up is switched on.
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebAuthnCredentialRegistrationListener implements ApplicationListener<WebAuthnCredentialRegisteredEvent> {

    private final UserEmailService userEmailService;
    private final ApplicationEventPublisher eventPublisher;
    private final WebAuthnConfigProperties webAuthnConfigProperties;

    /**
     * Audits the enrollment and, unless disabled, emails the account owner.
     *
     * @param event the registration event
     */
    @Override
    public void onApplicationEvent(WebAuthnCredentialRegisteredEvent event) {
        // Audit first and unconditionally: the notification is a courtesy the operator can switch off, and a mail
        // outage must not cost us the security-relevant record of the enrollment.
        eventPublisher.publishEvent(AuditEvent.builder().source(this).user(event.getUser())
                .action("PasskeyRegistration").actionStatus("Success")
                .message("Passkey registered: " + event.getLabel()).build());

        if (!webAuthnConfigProperties.isNotifyOnRegistration()) {
            return;
        }

        try {
            userEmailService.sendPasskeyRegisteredNotification(event.getUser(), event.getLabel());
        } catch (RuntimeException e) {
            // Never let a mail failure propagate into the registration flow, which has already committed.
            log.error("Failed to send passkey registration notification to user {}", event.getUser().getId(), e);
        }
    }
}
