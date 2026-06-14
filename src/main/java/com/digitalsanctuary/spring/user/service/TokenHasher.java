package com.digitalsanctuary.spring.user.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * Deterministically hashes verification and password-reset tokens before they are stored at rest.
 *
 * <p>
 * The raw token (a high-entropy, URL-safe value) is what gets emailed to the user. Only the
 * <em>hash</em> of that token is persisted in the database. On lookup, the service hashes the
 * incoming raw token and queries by the hash. Because the hash is deterministic, the same raw token
 * always maps to the same stored value, which is what makes lookup-by-hash possible.
 * </p>
 *
 * <p>
 * <strong>Keyed vs. plain.</strong> When {@code user.security.tokenHashSecret} is configured, this
 * class uses HMAC-SHA-256 keyed by that secret. Otherwise it falls back to a plain SHA-256 digest.
 * </p>
 * <ul>
 * <li><strong>Plain SHA-256</strong> is adequate here because the tokens are themselves high-entropy
 * random values (256 bits of entropy). An attacker who steals the database cannot feasibly reverse
 * the hash or guess the pre-image, so even unkeyed hashing prevents the stored value from being used
 * directly as a token.</li>
 * <li><strong>HMAC with a secret</strong> adds defense-in-depth against a database-only compromise:
 * without the application secret (stored outside the DB), an attacker cannot pre-compute or verify
 * candidate hashes offline at all. Configure a secret if you want the stored hashes to be useless to
 * anyone who only has the database.</li>
 * </ul>
 *
 * <p>
 * The output is a lowercase hexadecimal string (64 characters), which fits comfortably in the
 * existing {@code String token} column — so enabling hashing requires no schema migration.
 * </p>
 */
@Slf4j
@Component
public class TokenHasher {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String DIGEST_ALGORITHM = "SHA-256";

    /** Optional secret. When present, HMAC-SHA-256 is used; otherwise plain SHA-256. */
    private final String tokenHashSecret;

    /**
     * Instantiates a new token hasher.
     *
     * @param tokenHashSecret the optional secret used to key the HMAC; may be {@code null} or blank,
     *        in which case plain SHA-256 is used
     */
    public TokenHasher(@Value("${user.security.tokenHashSecret:#{null}}") final String tokenHashSecret) {
        this.tokenHashSecret = tokenHashSecret;
        if (StringUtils.hasText(tokenHashSecret)) {
            log.debug("TokenHasher initialized with a configured secret (HMAC-SHA-256).");
        } else {
            log.debug("TokenHasher initialized without a secret (plain SHA-256). "
                    + "Set user.security.tokenHashSecret for keyed hashing.");
        }
    }

    /**
     * Hashes the given raw token deterministically.
     *
     * @param rawToken the raw token value (the value emailed to the user)
     * @return the lowercase hex-encoded hash, or {@code null} if {@code rawToken} is {@code null}
     */
    public String hash(final String rawToken) {
        if (rawToken == null) {
            return null;
        }
        try {
            final byte[] digest;
            if (StringUtils.hasText(tokenHashSecret)) {
                final Mac mac = Mac.getInstance(HMAC_ALGORITHM);
                mac.init(new SecretKeySpec(tokenHashSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
                digest = mac.doFinal(rawToken.getBytes(StandardCharsets.UTF_8));
            } else {
                final MessageDigest md = MessageDigest.getInstance(DIGEST_ALGORITHM);
                digest = md.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            }
            return toHex(digest);
        } catch (final NoSuchAlgorithmException | java.security.InvalidKeyException e) {
            // SHA-256 / HmacSHA256 are guaranteed by the JCA spec; this should never happen.
            throw new IllegalStateException("Failed to hash token", e);
        }
    }

    /**
     * Produces a short, non-reversible fingerprint of a raw token for safe logging. Never logs the full
     * token: returns a fixed placeholder for {@code null}/short values and only the first 6 characters
     * (followed by an ellipsis) for longer tokens. Intended purely for correlating log lines, not for
     * any security decision.
     *
     * <p>
     * The returned prefix is stripped of CR/LF and other control characters so that an attacker-supplied
     * token (these values arrive as request parameters) cannot forge or split log lines (log injection).
     * </p>
     *
     * @param token the raw token (may be {@code null})
     * @return {@code "null"} if the token is {@code null}, {@code "****"} if it is 8 characters or fewer,
     *         otherwise the first 6 (control-character-stripped) characters followed by an ellipsis
     */
    public static String fingerprint(final String token) {
        if (token == null) {
            return "null";
        }
        if (token.length() <= 8) {
            return "****";
        }
        // Strip control characters (incl. CR/LF) from the logged prefix to prevent log injection/forging.
        final String prefix = token.substring(0, 6).replaceAll("\\p{Cntrl}", "");
        return prefix + "…";
    }

    /**
     * Converts a byte array to a lowercase hex string.
     *
     * @param bytes the bytes
     * @return the hex string
     */
    private static String toHex(final byte[] bytes) {
        final StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (final byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
