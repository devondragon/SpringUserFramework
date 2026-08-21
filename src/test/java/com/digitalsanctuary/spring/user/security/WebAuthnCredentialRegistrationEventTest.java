package com.digitalsanctuary.spring.user.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutableCredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCose;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialType;
import com.digitalsanctuary.spring.user.event.WebAuthnCredentialRegisteredEvent;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.model.WebAuthnCredential;
import com.digitalsanctuary.spring.user.persistence.model.WebAuthnUserEntity;
import com.digitalsanctuary.spring.user.persistence.repository.WebAuthnCredentialRepository;
import com.digitalsanctuary.spring.user.persistence.repository.WebAuthnUserEntityRepository;

/**
 * Enrolling a passkey is a credential-altering event the account owner should hear about, and the framework does not
 * own the endpoint that does it: Spring Security's {@code POST /webauthn/register} writes through
 * {@code JpaUserCredentialRepository}, which is the framework's. Publishing from there catches every enrollment
 * regardless of which endpoint triggered it.
 * <p>
 * The trap this pins: {@code UserCredentialRepository.save} is also called on every successful assertion, to persist
 * the updated signature count. Publishing on every save would email the user on each login.
 * </p>
 */
@DisplayName("WebAuthn Credential Registration Event Tests")
class WebAuthnCredentialRegistrationEventTest {

    private static final String CREDENTIAL_ID_B64 = "AQIDBA";

    private WebAuthnCredentialRepository credentialRepository;
    private WebAuthnUserEntityRepository userEntityRepository;
    private ApplicationEventPublisher eventPublisher;
    private WebAuthnRepositoryConfig.JpaUserCredentialRepository repository;
    private User user;

    @BeforeEach
    void setUp() {
        credentialRepository = mock(WebAuthnCredentialRepository.class);
        userEntityRepository = mock(WebAuthnUserEntityRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);

        user = new User();
        user.setId(7L);
        user.setEmail("passkey-user@test.com");

        WebAuthnUserEntity userEntity = new WebAuthnUserEntity();
        userEntity.setId("dXNlcg");
        userEntity.setName(user.getEmail());
        userEntity.setUser(user);
        when(userEntityRepository.findById(any())).thenReturn(Optional.of(userEntity));

        repository = new WebAuthnRepositoryConfig.JpaUserCredentialRepository(credentialRepository,
                userEntityRepository, eventPublisher);
    }

    @Test
    @DisplayName("should publish a registration event when the credential is new")
    void shouldPublishEventWhenCredentialIsNew() {
        when(credentialRepository.findById(CREDENTIAL_ID_B64)).thenReturn(Optional.empty());

        repository.save(credentialRecord("Work Laptop"));

        ArgumentCaptor<WebAuthnCredentialRegisteredEvent> captor =
                ArgumentCaptor.forClass(WebAuthnCredentialRegisteredEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getUser()).isEqualTo(user);
        assertThat(captor.getValue().getLabel()).isEqualTo("Work Laptop");
    }

    @Test
    @DisplayName("should publish no event when an existing credential is updated")
    void shouldPublishNoEventWhenCredentialIsUpdated() {
        // Spring Security calls save() inside authenticate() to persist the signature count. Treating that as a
        // registration would email the user on every passkey login.
        WebAuthnCredential existing = new WebAuthnCredential();
        existing.setCredentialId(CREDENTIAL_ID_B64);
        when(credentialRepository.findById(CREDENTIAL_ID_B64)).thenReturn(Optional.of(existing));

        repository.save(credentialRecord("Work Laptop"));

        verify(eventPublisher, never()).publishEvent(any(WebAuthnCredentialRegisteredEvent.class));
    }

    private static CredentialRecord credentialRecord(String label) {
        return ImmutableCredentialRecord.builder().credentialType(PublicKeyCredentialType.PUBLIC_KEY)
                .credentialId(new Bytes(new byte[] {1, 2, 3, 4}))
                .userEntityUserId(new Bytes("user".getBytes()))
                .publicKey(new ImmutablePublicKeyCose(new byte[] {9, 9}))
                .signatureCount(0).uvInitialized(true).backupEligible(false).backupState(false)
                .created(Instant.now()).lastUsed(Instant.now()).label(label).build();
    }
}
