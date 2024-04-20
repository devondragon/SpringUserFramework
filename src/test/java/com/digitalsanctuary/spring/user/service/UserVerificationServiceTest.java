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
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Calendar;
import java.util.Date;

import static org.mockito.ArgumentMatchers.anyString;
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

        userVerificationService = new UserVerificationService(
                userRepository,
                verificationTokenRepository
        );
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
        UserService.TokenValidationResult result = userVerificationService.validateVerificationToken(anyString());
        Assertions.assertEquals(result, UserService.TokenValidationResult.VALID);
    }

    @Test
    void validateVerificationToken_returnsExpiredIfTokenExpired() {
        testToken.setExpiryDate(getExpirationDate(0));
        when(verificationTokenRepository.findByToken(anyString())).thenReturn(testToken);
        UserService.TokenValidationResult result = userVerificationService.validateVerificationToken(anyString());
        Assertions.assertEquals(result, UserService.TokenValidationResult.EXPIRED);
    }

    @Test
    void validateVerificationToken_returnInvalidTokenIfTokenNotFound() {
        when(verificationTokenRepository.findByToken(anyString())).thenReturn(null);
        UserService.TokenValidationResult result = userVerificationService.validateVerificationToken(anyString());
        Assertions.assertEquals(result, UserService.TokenValidationResult.INVALID_TOKEN);
    }

    private Date getExpirationDate(int amount) {
        Date dt = new Date();
        Calendar c = Calendar.getInstance();
        c.setTime(dt);
        c.add(Calendar.DATE, amount);
        return c.getTime();
    }

}
