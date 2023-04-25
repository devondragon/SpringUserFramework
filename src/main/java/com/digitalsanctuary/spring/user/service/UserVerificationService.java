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
        log.debug("UserVerificationService.getUserByVerificationToken: called with token: {}", verificationToken);
        final VerificationToken token = tokenRepository.findByToken(verificationToken);
        if (token != null) {
            log.debug("UserVerificationService.getUserByVerificationToken: user found: {}", token.getUser());
            return token.getUser();
        }
        log.debug("UserVerificationService.getUserByVerificationToken: no user found!");
        return null;
    }

    /**
     * Gets the verification token.
     *
     * @param VerificationToken the verification token
     * @return the verification token
     */
    public VerificationToken getVerificationToken(final String VerificationToken) {
        return tokenRepository.findByToken(VerificationToken);
    }

    /**
     * Generate new verification token.
     *
     * @param existingVerificationToken the existing verification token
     * @return the verification token
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
     * Validate verification token.
     *
     * @param token the token
     * @return the string
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
        log.debug("UserVerificationService.deleteVerificationToken: called with token: {}", token);
        final VerificationToken verificationToken = tokenRepository.findByToken(token);
        if (verificationToken != null) {
            tokenRepository.delete(verificationToken);
            log.debug("UserVerificationService.deleteVerificationToken: token deleted.");
        } else {
            log.debug("UserVerificationService.deleteVerificationToken: token not found.");
        }
    }

}
