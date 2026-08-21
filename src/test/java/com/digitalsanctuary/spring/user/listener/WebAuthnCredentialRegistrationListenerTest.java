package com.digitalsanctuary.spring.user.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import com.digitalsanctuary.spring.user.audit.AuditEvent;
import com.digitalsanctuary.spring.user.event.WebAuthnCredentialRegisteredEvent;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.security.WebAuthnConfigProperties;
import com.digitalsanctuary.spring.user.service.UserEmailService;

/**
 * A newly enrolled passkey is a durable new way into the account that outlives a password change, so the owner is
 * told about it and the enrollment is recorded in the audit log.
 */
@DisplayName("WebAuthn Credential Registration Listener Tests")
class WebAuthnCredentialRegistrationListenerTest {

    private UserEmailService userEmailService;
    private ApplicationEventPublisher eventPublisher;
    private WebAuthnConfigProperties config;
    private User user;

    @BeforeEach
    void setUp() {
        userEmailService = mock(UserEmailService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        config = new WebAuthnConfigProperties();
        user = new User();
        user.setId(3L);
        user.setEmail("passkey-user@test.com");
    }

    private WebAuthnCredentialRegisteredEvent event() {
        return new WebAuthnCredentialRegisteredEvent(this, user, "AQIDBA", "Work Laptop");
    }

    private WebAuthnCredentialRegistrationListener listener() {
        return new WebAuthnCredentialRegistrationListener(userEmailService, eventPublisher, config);
    }

    @Test
    @DisplayName("should record an audit event when a passkey is registered")
    void shouldRecordAuditEventWhenPasskeyRegistered() {
        listener().onCredentialRegistered(event());

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getAction()).isEqualTo("PasskeyRegistration");
        assertThat(captor.getValue().getActionStatus()).isEqualTo("Success");
        assertThat(captor.getValue().getUser()).isEqualTo(user);
    }

    @Test
    @DisplayName("should notify the account owner by email when a passkey is registered")
    void shouldNotifyOwnerWhenPasskeyRegistered() {
        listener().onCredentialRegistered(event());

        verify(userEmailService).sendPasskeyRegisteredNotification(user, "Work Laptop");
    }

    @Test
    @DisplayName("should still record the audit event when notification email is disabled")
    void shouldStillAuditWhenNotificationDisabled() {
        // The email is a courtesy the operator may not want; the audit trail is not optional.
        config.setNotifyOnRegistration(false);

        listener().onCredentialRegistered(event());

        verify(userEmailService, never()).sendPasskeyRegisteredNotification(any(), any());
        verify(eventPublisher).publishEvent(any(AuditEvent.class));
    }

    @Test
    @DisplayName("should record the audit event even when sending the notification fails")
    void shouldAuditEvenWhenNotificationFails() {
        // A mail outage must not lose the security-relevant record of the enrollment.
        org.mockito.Mockito.doThrow(new RuntimeException("smtp down")).when(userEmailService)
                .sendPasskeyRegisteredNotification(any(), any());

        listener().onCredentialRegistered(event());

        verify(eventPublisher).publishEvent(any(AuditEvent.class));
    }

    @Test
    @DisplayName("should react only after the enrollment transaction commits")
    void shouldReactAfterCommit() throws NoSuchMethodException {
        // The credential is written through a @Transactional save() that publishes the event before commit. Reacting
        // after commit stops a rolled-back registration (e.g. a label too long for the column) from emailing the
        // owner and recording an audit entry for a passkey that never persisted.
        TransactionalEventListener annotation = WebAuthnCredentialRegistrationListener.class
                .getMethod("onCredentialRegistered", WebAuthnCredentialRegisteredEvent.class)
                .getAnnotation(TransactionalEventListener.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
        assertThat(annotation.fallbackExecution()).isTrue();
    }
}
