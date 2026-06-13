package com.digitalsanctuary.spring.user.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.model.WebAuthnCredential;
import com.digitalsanctuary.spring.user.persistence.model.WebAuthnUserEntity;
import com.digitalsanctuary.spring.user.test.annotations.DatabaseTest;
import com.digitalsanctuary.spring.user.test.builders.UserTestDataBuilder;

/**
 * Repository-slice IDOR (Insecure Direct Object Reference) negative tests for WebAuthn credential
 * ownership enforcement.
 *
 * <p>
 * The ownership guard lives in {@link WebAuthnCredentialQueryRepository#deleteCredential(String, Long)}
 * and {@link WebAuthnCredentialQueryRepository#renameCredential(String, String, Long)}: each loads the
 * credential by its id, then verifies the credential's owning {@code WebAuthnUserEntity.user.id} matches
 * the supplied {@code userId} before acting. These tests prove that guard holds against a real (H2)
 * database — not just a mock — so that user A cannot delete or rename user B's passkey by guessing its
 * credential id.
 * </p>
 *
 * <p>
 * This runs as a {@code @DataJpaTest} slice (transactional, rolled back per test), so it does not pollute
 * the shared integration database. {@link WebAuthnCredentialQueryRepository} is conditional on
 * {@code user.webauthn.enabled} and is not part of the repository slice scan, so it is constructed
 * manually from the autowired {@link WebAuthnCredentialRepository}.
 * </p>
 */
@DatabaseTest
class WebAuthnCredentialOwnershipTest {

    @Autowired
    private WebAuthnCredentialRepository credentialRepository;

    @Autowired
    private TestEntityManager entityManager;

    private WebAuthnCredentialQueryRepository queryRepository;

    private User userA;
    private User userB;
    private WebAuthnCredential credA;
    private WebAuthnCredential credB;

    private static final String CRED_B_ORIGINAL_LABEL = "User B's iPhone";

    @BeforeEach
    void setUp() {
        // The query repository is @ConditionalOnProperty(user.webauthn.enabled) and is not picked up by
        // the @DataJpaTest slice scan, so wire it manually from the slice-managed Spring Data repository.
        queryRepository = new WebAuthnCredentialQueryRepository(credentialRepository);

        userA = persistUser("idor-owner-a@test.com");
        userB = persistUser("idor-owner-b@test.com");

        credA = persistCredential("cred-a-id", "User A's YubiKey", userA, "handle-a");
        credB = persistCredential("cred-b-id", CRED_B_ORIGINAL_LABEL, userB, "handle-b");
    }

    private User persistUser(String email) {
        User user = UserTestDataBuilder.aUser().withId(null).withEmail(email).build();
        return entityManager.persistAndFlush(user);
    }

    private WebAuthnCredential persistCredential(String credentialId, String label, User owner, String userHandle) {
        WebAuthnUserEntity userEntity = new WebAuthnUserEntity();
        userEntity.setId(userHandle);
        userEntity.setName(owner.getEmail());
        userEntity.setDisplayName(owner.getFirstName() + " " + owner.getLastName());
        userEntity.setUser(owner);
        entityManager.persist(userEntity);

        WebAuthnCredential credential = new WebAuthnCredential();
        credential.setCredentialId(credentialId);
        credential.setUserEntity(userEntity);
        credential.setPublicKey(new byte[] {1, 2, 3, 4});
        credential.setSignatureCount(0L);
        credential.setLabel(label);
        credential.setCreated(Instant.now());
        entityManager.persist(credential);
        entityManager.flush();
        return credential;
    }

    @Test
    void shouldReturnZeroAndPreserveCredentialWhenDeletingAnotherUsersCredential() {
        int deleted = queryRepository.deleteCredential(credB.getCredentialId(), userA.getId());

        assertThat(deleted).as("user A must not be able to delete user B's credential").isZero();

        // Reload from the database to confirm the credential still exists.
        entityManager.clear();
        assertThat(credentialRepository.findById(credB.getCredentialId()))
                .as("user B's credential must still exist after the rejected cross-user delete").isPresent();
    }

    @Test
    void shouldReturnZeroAndPreserveLabelWhenRenamingAnotherUsersCredential() {
        int renamed = queryRepository.renameCredential(credB.getCredentialId(), "hacked", userA.getId());

        assertThat(renamed).as("user A must not be able to rename user B's credential").isZero();

        // Reload from the database to confirm the label is unchanged.
        entityManager.clear();
        assertThat(credentialRepository.findById(credB.getCredentialId()))
                .as("user B's credential must still exist after the rejected cross-user rename").isPresent()
                .get().extracting(WebAuthnCredential::getLabel)
                .as("user B's credential label must be unchanged").isEqualTo(CRED_B_ORIGINAL_LABEL);
    }

    @Test
    void shouldDeleteOwnCredentialWhenOwnerRequestsDeletion() {
        // Positive control: the owner CAN delete their own credential. Proves the guard rejects based on
        // ownership rather than rejecting everything, so the negative assertions above are meaningful.
        int deleted = queryRepository.deleteCredential(credA.getCredentialId(), userA.getId());

        assertThat(deleted).as("user A must be able to delete their own credential").isEqualTo(1);

        // Flush the pending delete to the DB, then clear so the reload hits the database rather than the
        // first-level cache. (clear() alone would discard the un-flushed delete and falsely show the row.)
        entityManager.flush();
        entityManager.clear();
        assertThat(credentialRepository.findById(credA.getCredentialId()))
                .as("user A's credential must be gone after the owner deletes it").isEmpty();
    }
}
