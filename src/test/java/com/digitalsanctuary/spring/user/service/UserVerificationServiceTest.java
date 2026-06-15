package com.digitalsanctuary.spring.user.service;

import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.model.VerificationToken;
import com.digitalsanctuary.spring.user.persistence.repository.UserRepository;
import com.digitalsanctuary.spring.user.persistence.repository.VerificationTokenRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Calendar;
import java.util.Date;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class UserVerificationServiceTest {

    private UserVerificationService userVerificationService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private VerificationTokenRepository verificationTokenRepository;
    private User testUser;
    private VerificationToken testToken;


    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setEmail("test@example.com");
        testUser.setFirstName("testFirstName");
        testUser.setLastName("testLastName");
        testUser.setPassword("testPassword");

        testToken = new VerificationToken();
        testToken.setUser(testUser);

        userVerificationService = new UserVerificationService(userRepository, verificationTokenRepository, new TokenHasher(null));
    }

    @Test
    void getUserByVerificationToken_returnsUserIfTokenExist() {
        when(verificationTokenRepository.findByToken(anyString())).thenReturn(testToken);
        User found = userVerificationService.getUserByVerificationToken(anyString());
        Assertions.assertEquals(found, testUser);
    }

    @Test
    void validateVerificationToken_returnsValidIfTokenValid() {
        testToken.setExpiryDate(getExpirationDate(1));
        when(verificationTokenRepository.findByToken(anyString())).thenReturn(testToken);
        // The conditional delete is the single-use guard: a count of 1 means THIS call consumed the token.
        when(verificationTokenRepository.deleteByToken(anyString())).thenReturn(1);
        UserService.TokenValidationResult result = userVerificationService.validateVerificationToken("raw-token");
        Assertions.assertEquals(UserService.TokenValidationResult.VALID, result);
        // The user is enabled only after winning the delete; the token is consumed so it is strictly single-use.
        Assertions.assertTrue(testUser.isEnabled());
        Mockito.verify(userRepository).save(testUser);
        Mockito.verify(verificationTokenRepository).deleteByToken(anyString());
    }

    @Test
    void validateVerificationToken_returnsExpiredIfTokenExpired() {
        // Clearly-past expiry so the token is unambiguously expired.
        testToken.setExpiryDate(getExpirationDate(-1));
        // Dual-read: validateVerificationToken first looks up by hash(raw), then by raw. With a null
        // secret the hash is a deterministic SHA-256 of the raw value, so stub findByToken for any
        // argument to resolve the token regardless of which lookup the service performs.
        when(verificationTokenRepository.findByToken(anyString())).thenReturn(testToken);
        when(verificationTokenRepository.deleteByToken(anyString())).thenReturn(1);

        UserService.TokenValidationResult result = userVerificationService.validateVerificationToken("raw-token");

        Assertions.assertEquals(UserService.TokenValidationResult.EXPIRED, result);
        // Expired tokens are still consumed (deleted) as part of validation, but the user is NOT enabled.
        Mockito.verify(verificationTokenRepository).deleteByToken(anyString());
        Mockito.verify(userRepository, never()).save(testToken.getUser());
        Assertions.assertFalse(testUser.isEnabled());
    }

    @Test
    void validateVerificationToken_returnInvalidTokenIfTokenNotFound() {
        when(verificationTokenRepository.findByToken(anyString())).thenReturn(null);
        UserService.TokenValidationResult result = userVerificationService.validateVerificationToken(anyString());
        Assertions.assertEquals(result, UserService.TokenValidationResult.INVALID_TOKEN);
    }

    @Test
    void validateVerificationToken_returnsInvalidWhenConcurrentlyConsumed() {
        // The token resolves (a concurrent caller has not yet committed its delete) but our conditional delete
        // removes 0 rows because the other caller won the race. We must NOT enable the user in that case.
        testToken.setExpiryDate(getExpirationDate(1));
        when(verificationTokenRepository.findByToken(anyString())).thenReturn(testToken);
        when(verificationTokenRepository.deleteByToken(anyString())).thenReturn(0);

        UserService.TokenValidationResult result = userVerificationService.validateVerificationToken("raw-token");

        Assertions.assertEquals(UserService.TokenValidationResult.INVALID_TOKEN, result);
        Assertions.assertFalse(testUser.isEnabled());
        Mockito.verify(userRepository, never()).save(testToken.getUser());
    }

    private Date getExpirationDate(int amount) {
        Date dt = new Date();
        Calendar c = Calendar.getInstance();
        c.setTime(dt);
        c.add(Calendar.DATE, amount);
        return c.getTime();
    }

}
