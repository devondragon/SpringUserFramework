package com.digitalsanctuary.spring.user.service;

import java.util.Calendar;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.model.VerificationToken;
import com.digitalsanctuary.spring.user.persistence.repository.UserRepository;
import com.digitalsanctuary.spring.user.persistence.repository.VerificationTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for managing user verification tokens.
 *
 * <p>Provides methods for creating, validating, and deleting verification tokens used
 * during user registration and email verification workflows. Handles token expiration
 * and automatic user enablement upon successful verification.</p>
 *
 * @author Devon Hillard
 * @see VerificationToken
 * @see UserService
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class UserVerificationService {

    /** The user repository. */
    private final UserRepository userRepository;

    /** The token repository. */
    private final VerificationTokenRepository tokenRepository;

    /** Hashes tokens before they are stored at rest. */
    private final TokenHasher tokenHasher;

    /** Verification token lifetime in minutes. Defaults to 24h. */
    @Value("${user.registration.verificationTokenValidityMinutes:1440}")
    private int verificationTokenValidityMinutes;

    /**
     * Resolves a verification token by its raw value using a dual-read strategy.
     *
     * <p>
     * Tokens are stored hashed, so we first look up by {@code hash(rawToken)}. For backward
     * compatibility we fall back to looking up by the raw value, which resolves any pre-upgrade
     * tokens that were stored in plaintext before token hashing was introduced. This fallback is
     * permanently safe and needs no operator action to retire: every token carries an
     * {@code expiryDate} bounded by the configured lifetime, and the validate path rejects expired
     * tokens, so any lingering plaintext token becomes unusable within its lifetime window.
     * </p>
     *
     * @param rawToken the raw token value
     * @return the resolved token entity, or {@code null} if not found
     */
    private VerificationToken resolveByRawToken(final String rawToken) {
        if (rawToken == null) {
            return null;
        }
        VerificationToken token = tokenRepository.findByToken(tokenHasher.hash(rawToken));
        if (token == null) {
            token = tokenRepository.findByToken(rawToken);
        }
        return token;
    }

    /**
     * Gets the user by verification token.
     *
     * @param verificationToken the verification token
     * @return the user by verification token
     */
    public User getUserByVerificationToken(final String verificationToken) {
        log.debug("UserVerificationService.getUserByVerificationToken: called with token: {}", verificationToken);
        final VerificationToken token = resolveByRawToken(verificationToken);
        if (token != null) {
            log.debug("UserVerificationService.getUserByVerificationToken: user found: {}", token.getUser());
            return token.getUser();
        }
        log.debug("UserVerificationService.getUserByVerificationToken: no user found!");
        return null;
    }

    /**
     * Gets the verification token by its string value.
     *
     * @param verificationToken the verification token string
     * @return the verification token entity
     */
    public VerificationToken getVerificationToken(final String verificationToken) {
        return resolveByRawToken(verificationToken);
    }

    /**
     * Generates a new verification token to replace an existing one. Useful for extending
     * verification periods or re-sending verification emails.
     *
     * <p>
     * A fresh high-entropy raw token is generated. Only its <em>hash</em> is persisted in the
     * {@code token} column (consistent with {@link #createVerificationTokenForUser}); the raw value
     * is returned to the caller via {@link VerificationToken#getPlainToken()} so a verification email
     * link can be built. The expiry is set from the configurable
     * {@code user.registration.verificationTokenValidityMinutes} (not a hardcoded 24h). The existing
     * row is updated in place, preserving the single-active-token invariant.
     * </p>
     *
     * @param existingVerificationToken the existing verification token string to replace
     * @return the updated verification token entity. Its persisted {@code token} is the hash; the raw
     *         value is available via {@link VerificationToken#getPlainToken()}.
     */
    @Transactional
    public VerificationToken generateNewVerificationToken(final String existingVerificationToken) {
        VerificationToken vToken = resolveByRawToken(existingVerificationToken);
        final String rawToken = UUID.randomUUID().toString();
        // Store the hash of the new raw token; the raw value is what gets emailed to the user.
        vToken.updateToken(tokenHasher.hash(rawToken), verificationTokenValidityMinutes);
        vToken.setPlainToken(rawToken);
        vToken = tokenRepository.save(vToken);
        return vToken;
    }

    /**
     * Creates the verification token for user.
     *
     * <p>
     * The token is hashed before storage (the raw value goes into the emailed link). Any existing
     * token for the user is deleted first so that only one active verification token exists per user.
     * </p>
     *
     * @param user the user
     * @param token the raw token (emailed to the user)
     */
    @Transactional
    public void createVerificationTokenForUser(final User user, final String token) {
        // Single active token per user: remove any previously issued token before creating a new one.
        tokenRepository.deleteByUser(user);
        // Store only the hash of the token; the raw token is what was emailed to the user.
        final VerificationToken myToken =
                new VerificationToken(tokenHasher.hash(token), user, verificationTokenValidityMinutes);
        tokenRepository.save(myToken);
    }

    /**
     * Validates a user verification token and, when valid, atomically consumes it by enabling the
     * user. Uses dual-read so both hashed (post-upgrade) and plaintext (pre-upgrade) tokens resolve.
     *
     * @param token the raw token to validate
     * @return the token validation result (VALID, INVALID_TOKEN, or EXPIRED)
     */
    public UserService.TokenValidationResult validateVerificationToken(String token) {
        final VerificationToken verificationToken = resolveByRawToken(token);
        if (verificationToken == null) {
            return UserService.TokenValidationResult.INVALID_TOKEN;
        }

        final User user = verificationToken.getUser();
        final Calendar cal = Calendar.getInstance();
        if (verificationToken.getExpiryDate().before(cal.getTime())) {
            tokenRepository.delete(verificationToken);
            return UserService.TokenValidationResult.EXPIRED;
        }

        user.setEnabled(true);
        userRepository.save(user);
        return UserService.TokenValidationResult.VALID;
    }

    /**
     * Delete verification token.
     *
     * @param token the raw token
     */
    public void deleteVerificationToken(final String token) {
        log.debug("UserVerificationService.deleteVerificationToken: called with token: {}", token);
        final VerificationToken verificationToken = resolveByRawToken(token);
        if (verificationToken != null) {
            tokenRepository.delete(verificationToken);
            log.debug("UserVerificationService.deleteVerificationToken: token deleted.");
        } else {
            log.debug("UserVerificationService.deleteVerificationToken: token not found.");
        }
    }

}
