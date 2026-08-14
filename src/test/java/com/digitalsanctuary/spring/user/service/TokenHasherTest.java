package com.digitalsanctuary.spring.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.digitalsanctuary.spring.user.security.UserSecurityConfigProperties;

/**
 * Unit tests for {@link TokenHasher}.
 */
@DisplayName("TokenHasher Tests")
class TokenHasherTest {

    /**
     * Builds a real {@link UserSecurityConfigProperties} with the given (possibly {@code null} or blank)
     * {@code tokenHashSecret} and constructs a {@link TokenHasher} from it.
     */
    private static TokenHasher newTokenHasher(String secret) {
        UserSecurityConfigProperties config = new UserSecurityConfigProperties();
        config.setTokenHashSecret(secret);
        return new TokenHasher(config);
    }

    @Test
    @DisplayName("hash is deterministic - same input yields same output (plain SHA-256)")
    void shouldProduceDeterministicHashWhenNoSecretConfigured() {
        TokenHasher hasher = newTokenHasher(null);
        String raw = "my-high-entropy-token";

        String first = hasher.hash(raw);
        String second = hasher.hash(raw);

        assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("hashed value is not equal to the raw token")
    void shouldNotReturnRawTokenWhenHashing() {
        TokenHasher hasher = newTokenHasher(null);
        String raw = "my-high-entropy-token";

        assertThat(hasher.hash(raw)).isNotEqualTo(raw);
    }

    @Test
    @DisplayName("hash output is a 64-char lowercase hex string (SHA-256)")
    void shouldReturnHexEncodedSha256() {
        TokenHasher hasher = newTokenHasher(null);

        assertThat(hasher.hash("token")).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("keyed HMAC differs from plain SHA-256 for the same input")
    void shouldProduceDifferentHashWhenSecretConfigured() {
        TokenHasher plain = newTokenHasher(null);
        TokenHasher keyed = newTokenHasher("super-secret-key");
        String raw = "my-high-entropy-token";

        assertThat(keyed.hash(raw)).isNotEqualTo(plain.hash(raw));
    }

    @Test
    @DisplayName("keyed HMAC is deterministic with the same secret")
    void shouldProduceDeterministicHashWhenSecretConfigured() {
        TokenHasher keyed = newTokenHasher("super-secret-key");
        String raw = "my-high-entropy-token";

        assertThat(keyed.hash(raw)).isEqualTo(keyed.hash(raw));
    }

    @Test
    @DisplayName("blank secret falls back to plain SHA-256 behavior")
    void shouldTreatBlankSecretAsUnset() {
        TokenHasher blank = newTokenHasher("   ");
        TokenHasher plain = newTokenHasher(null);

        assertThat(blank.hash("token")).isEqualTo(plain.hash("token"));
    }
}
