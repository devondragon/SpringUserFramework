package com.digitalsanctuary.spring.user.service;

import java.util.Calendar;
import java.util.UUID;
import org.springframework.stereotype.Service;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.model.VerificationToken;
import com.digitalsanctuary.spring.user.persistence.repository.UserRepository;
import com.digitalsanctuary.spring.user.persistence.repository.VerificationTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The UserVerificationService class is a Spring service class that provides methods for managing user verification tokens. This class is used to
 * create, validate, and delete verification tokens for users.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class UserVerificationService {

    /** The user repository. */
    private final UserRepository userRepository;

    /** The token repository. */
    private final VerificationTokenRepository tokenRepository;

    /**
     * Gets the user by verification token.
     *
     * @param verificationToken the verification token
     * @return the user by verification token
     */
    public User getUserByVerificationToken(final String verificationToken) {
        log.debug("UserVerificationService.getUserByVerificationToken: called with token: {}", tokenFingerprint(verificationToken));
        final VerificationToken token = tokenRepository.findByToken(verificationToken);
        if (token != null) {
            log.debug("UserVerificationService.getUserByVerificationToken: user found: {}",
                    token.getUser() != null ? token.getUser().getEmail() : null);
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
        return tokenRepository.findByToken(verificationToken);
    }

    /**
     * Generates a new verification token to replace an existing one.
     * Useful for extending verification periods or re-sending verification emails.
     *
     * @param existingVerificationToken the existing verification token string to replace
     * @return the updated verification token entity with a new token value
     */
    public VerificationToken generateNewVerificationToken(final String existingVerificationToken) {
        VerificationToken vToken = tokenRepository.findByToken(existingVerificationToken);
        vToken.updateToken(UUID.randomUUID().toString());
        vToken = tokenRepository.save(vToken);
        return vToken;
    }

    /**
     * Creates the verification token for user.
     *
     * @param user the user
     * @param token the token
     */
    public void createVerificationTokenForUser(final User user, final String token) {
        final VerificationToken myToken = new VerificationToken(token, user);
        tokenRepository.save(myToken);
    }

    /**
     * Validates a user verification token.
     *
     * @param token the token to validate
     * @return the token validation result (VALID, INVALID_TOKEN, or EXPIRED)
     */
    public UserService.TokenValidationResult validateVerificationToken(String token) {
        final VerificationToken verificationToken = tokenRepository.findByToken(token);
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
     * @param token the token
     */
    public void deleteVerificationToken(final String token) {
        log.debug("UserVerificationService.deleteVerificationToken: called with token: {}", tokenFingerprint(token));
        final VerificationToken verificationToken = tokenRepository.findByToken(token);
        if (verificationToken != null) {
            tokenRepository.delete(verificationToken);
            log.debug("UserVerificationService.deleteVerificationToken: token deleted.");
        } else {
            log.debug("UserVerificationService.deleteVerificationToken: token not found.");
        }
    }

    /**
     * Produces a non-reversible fingerprint of a token for safe logging. Never logs the full token
     * value (which is sensitive authentication material).
     *
     * @param token the raw token value
     * @return "null" if the token is null, "****" for short tokens, otherwise the first 6 characters
     *         followed by an ellipsis
     */
    private String tokenFingerprint(final String token) {
        if (token == null) {
            return "null";
        }
        if (token.length() <= 8) {
            return "****";
        }
        return token.substring(0, 6) + "…";
    }

}
