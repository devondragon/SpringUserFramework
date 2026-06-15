package com.digitalsanctuary.spring.user.persistence.model;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

/**
 * Unit tests verifying the identity-based ({@code id}-only) {@code equals}/{@code hashCode} contract and the
 * secret-safe {@code toString} on the JPA entities migrated away from Lombok {@code @Data}.
 *
 * <p>Under id-only equality two managed instances are equal only when they share a non-null id; instances with
 * different ids (or a null id) are not equal. {@code toString} must never include passwords or token secrets, as
 * it is commonly emitted to logs.</p>
 */
class EntityEqualityTest {

    @Test
    void shouldBeEqualAndShareHashCodeWhenUsersHaveSameId() {
        // Given two distinct User instances with the same id but otherwise different field values
        User first = new User();
        first.setId(1L);
        first.setEmail("first@test.com");

        User second = new User();
        second.setId(1L);
        second.setEmail("second@test.com");

        // Then they are equal and share a hash code, regardless of differing non-id fields
        assertThat(first).isEqualTo(second);
        assertThat(first).hasSameHashCodeAs(second);
    }

    @Test
    void shouldNotBeEqualWhenUsersHaveDifferentIds() {
        // Given two User instances with different ids
        User first = new User();
        first.setId(1L);

        User second = new User();
        second.setId(2L);

        // Then they are not equal
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void shouldNotBeEqualWhenOneUserHasNullId() {
        // Given two User instances, one with a null id (transient/unsaved)
        User saved = new User();
        saved.setId(1L);

        User transientUser = new User();

        // Then they are not equal under id-only equality
        assertThat(saved).isNotEqualTo(transientUser);
    }

    @Test
    void shouldNotIncludePasswordWhenUserToStringCalled() {
        // Given a user carrying a (fake) password hash
        User user = new User();
        user.setId(1L);
        user.setEmail("user@test.com");
        user.setPassword("SUPERSECRET_HASH");

        // Then the rendered string excludes the password hash
        assertThat(user.toString()).doesNotContain("SUPERSECRET_HASH");
    }

    @Test
    void shouldBeEqualAndShareHashCodeWhenVerificationTokensHaveSameId() {
        // Given two distinct VerificationToken instances with the same id but different secret values
        VerificationToken first = new VerificationToken("token-aaa");
        first.setId(10L);

        VerificationToken second = new VerificationToken("token-bbb");
        second.setId(10L);

        // Then they are equal and share a hash code
        assertThat(first).isEqualTo(second);
        assertThat(first).hasSameHashCodeAs(second);
    }

    @Test
    void shouldNotBeEqualWhenVerificationTokensHaveDifferentIds() {
        // Given two VerificationToken instances with different ids
        VerificationToken first = new VerificationToken("token");
        first.setId(10L);

        VerificationToken second = new VerificationToken("token");
        second.setId(20L);

        // Then they are not equal even with the same token value
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void shouldNotBeEqualWhenOneVerificationTokenHasNullId() {
        // Given two VerificationToken instances, one with a null id
        VerificationToken saved = new VerificationToken("token");
        saved.setId(10L);

        VerificationToken transientToken = new VerificationToken("token");

        // Then they are not equal under id-only equality
        assertThat(saved).isNotEqualTo(transientToken);
    }

    @Test
    void shouldNotIncludeTokenSecretWhenVerificationTokenToStringCalled() {
        // Given a verification token holding hashed and raw secret values
        VerificationToken token = new VerificationToken("HASHED_TOKEN_SECRET");
        token.setId(10L);
        token.setPlainToken("RAW_TOKEN_SECRET");

        // When rendered to a string
        String rendered = token.toString();

        // Then neither the hashed nor the raw token secret appears
        assertThat(rendered).doesNotContain("HASHED_TOKEN_SECRET");
        assertThat(rendered).doesNotContain("RAW_TOKEN_SECRET");
    }

    @Test
    void shouldBeEqualAndShareHashCodeWhenWebAuthnCredentialsHaveSameCredentialId() {
        // Given two distinct WebAuthnCredential instances with the same natural-key credentialId
        WebAuthnCredential first = new WebAuthnCredential();
        first.setCredentialId("AAAA-same-credential-id");
        first.setLabel("My iPhone");

        WebAuthnCredential second = new WebAuthnCredential();
        second.setCredentialId("AAAA-same-credential-id");
        second.setLabel("Some Other Label");

        // Then they are equal and share a hash code, regardless of differing non-key fields
        assertThat(first).isEqualTo(second);
        assertThat(first).hasSameHashCodeAs(second);
    }

    @Test
    void shouldNotBeEqualWhenWebAuthnCredentialsHaveDifferentCredentialIds() {
        // Given two WebAuthnCredential instances with different credentialIds
        WebAuthnCredential first = new WebAuthnCredential();
        first.setCredentialId("AAAA-credential-one");

        WebAuthnCredential second = new WebAuthnCredential();
        second.setCredentialId("BBBB-credential-two");

        // Then they are not equal
        assertThat(first).isNotEqualTo(second);
    }
}
