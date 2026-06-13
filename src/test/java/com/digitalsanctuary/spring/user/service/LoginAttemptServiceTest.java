package com.digitalsanctuary.spring.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

    @Mock
    private UserRepository userRepository;

    private LoginAttemptService loginAttemptService;

    private final int failedLoginAttempts = 10; // Assuming these are the values in your application.properties
    private final int accountLockoutDuration = 1; // Assuming these are the values in your application.properties

    private User testUser;

    @BeforeEach
    void setUp() {
        // Initialize your test user here
        testUser = new User();
        testUser.setEmail("test@example.com");
        testUser.setFailedLoginAttempts(0);
        testUser.setLocked(false);

        // Manually construct the service with mocked dependencies
        loginAttemptService = new LoginAttemptService(userRepository);
        loginAttemptService.setMaxFailedLoginAttempts(failedLoginAttempts);
        loginAttemptService.setAccountLockoutDuration(accountLockoutDuration);
    }

    @Test
    void loginSucceeded_resetsFailedAttempts() {
        when(userRepository.findByEmail(anyString())).thenReturn(testUser);

        loginAttemptService.loginSucceeded(testUser.getEmail());

        assertThat(testUser.getFailedLoginAttempts()).isZero();
        assertThat(testUser.isLocked()).isFalse();
        assertThat(testUser.getLockedDate()).isNull();
        verify(userRepository).save(testUser);
    }

    @Test
    void loginFailed_callsAtomicIncrementAndLocksAtThreshold() {
        // The atomic UPDATE reports one row affected (the user exists).
        when(userRepository.incrementFailedAttempts(testUser.getEmail())).thenReturn(1);
        // Re-read returns the user whose counter has reached the threshold (simulating the fresh DB value after the bulk update + clear).
        testUser.setFailedLoginAttempts(failedLoginAttempts);
        when(userRepository.findByEmail(testUser.getEmail())).thenReturn(testUser);

        loginAttemptService.loginFailed(testUser.getEmail());

        verify(userRepository).incrementFailedAttempts(testUser.getEmail());
        verify(userRepository).findByEmail(testUser.getEmail());
        assertThat(testUser.isLocked()).isTrue();
        assertThat(testUser.getLockedDate()).isNotNull();
        verify(userRepository).save(testUser);
    }

    @Test
    void loginFailed_doesNotLockBelowThreshold() {
        when(userRepository.incrementFailedAttempts(testUser.getEmail())).thenReturn(1);
        // Re-read returns the user with a count below the lockout threshold.
        testUser.setFailedLoginAttempts(failedLoginAttempts - 1);
        when(userRepository.findByEmail(testUser.getEmail())).thenReturn(testUser);

        loginAttemptService.loginFailed(testUser.getEmail());

        verify(userRepository).incrementFailedAttempts(testUser.getEmail());
        assertThat(testUser.isLocked()).isFalse();
        assertThat(testUser.getLockedDate()).isNull();
        verify(userRepository, never()).save(testUser);
    }

    @Test
    void loginFailed_warnsAndStopsWhenUserNotFound() {
        // The atomic UPDATE affected no rows, meaning the user does not exist.
        when(userRepository.incrementFailedAttempts(anyString())).thenReturn(0);

        loginAttemptService.loginFailed("missing@example.com");

        verify(userRepository).incrementFailedAttempts("missing@example.com");
        verify(userRepository, never()).findByEmail(anyString());
        verify(userRepository, never()).save(testUser);
    }

    @Test
    void loginFailed_doesNothingWhenLockoutDisabled() {
        loginAttemptService.setMaxFailedLoginAttempts(0);

        loginAttemptService.loginFailed(testUser.getEmail());

        // When the feature is disabled, the atomic increment must not be invoked at all.
        verify(userRepository, never()).incrementFailedAttempts(anyString());
        verify(userRepository, never()).findByEmail(anyString());
        verify(userRepository, never()).save(testUser);
    }

    @Test
    void isLocked_returnsTrueWhenUserIsLocked() {
        testUser.setLocked(true);
        testUser.setLockedDate(new Date());

        when(userRepository.findByEmail(anyString())).thenReturn(testUser);

        assertThat(loginAttemptService.isLocked(testUser.getEmail())).isTrue();
    }

    @Test
    void isLocked_returnsFalseWhenUserIsNotLocked() {
        when(userRepository.findByEmail(anyString())).thenReturn(testUser);

        assertThat(loginAttemptService.isLocked(testUser.getEmail())).isFalse();
    }

    @Test
    void isLocked_unlocksUserAfterLockoutDuration() {
        // Set the user as locked with a lock date before the lockout duration
        testUser.setLocked(true);
        testUser.setLockedDate(new Date(System.currentTimeMillis() - (accountLockoutDuration + 1) * 60 * 1000));

        when(userRepository.findByEmail(anyString())).thenReturn(testUser);

        assertThat(loginAttemptService.isLocked(testUser.getEmail())).isFalse();
        assertThat(testUser.isLocked()).isFalse();
        assertThat(testUser.getLockedDate()).isNull();
        assertThat(testUser.getFailedLoginAttempts()).isZero();
        verify(userRepository).save(testUser);
    }

    @Test
    void checkIfUserShouldBeUnlocked_adminOnlyUnlockKeepsLockedDespitePastLockedDate() {
        // A negative accountLockoutDuration means the account can ONLY be unlocked by an administrator,
        // never automatically by elapsed time — even with a lockedDate far in the past.
        loginAttemptService.setAccountLockoutDuration(-1);
        testUser.setLocked(true);
        testUser.setLockedDate(new Date(System.currentTimeMillis() - 60L * 60 * 1000)); // locked an hour ago

        User result = loginAttemptService.checkIfUserShouldBeUnlocked(testUser);

        assertThat(result.isLocked()).isTrue();
        assertThat(result.getLockedDate()).isNotNull();
        // No auto-unlock occurred, so nothing should have been persisted.
        verify(userRepository, never()).save(testUser);
    }

    @Test
    void isLocked_adminOnlyUnlockKeepsUserLockedDespitePastLockedDate() {
        // End-to-end through isLocked(): with admin-only unlock, a long-locked user stays locked.
        loginAttemptService.setAccountLockoutDuration(-1);
        testUser.setLocked(true);
        testUser.setLockedDate(new Date(System.currentTimeMillis() - 60L * 60 * 1000));
        when(userRepository.findByEmail(anyString())).thenReturn(testUser);

        assertThat(loginAttemptService.isLocked(testUser.getEmail())).isTrue();
        assertThat(testUser.isLocked()).isTrue();
        verify(userRepository, never()).save(testUser);
    }

    // Additional tests can be written for edge cases and exception handling
}
