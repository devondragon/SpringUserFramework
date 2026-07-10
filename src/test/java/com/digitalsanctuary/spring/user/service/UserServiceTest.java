package com.digitalsanctuary.spring.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.digitalsanctuary.spring.user.dto.PasswordlessRegistrationDto;
import com.digitalsanctuary.spring.user.dto.UserDto;
import com.digitalsanctuary.spring.user.event.UserDeletedEvent;
import com.digitalsanctuary.spring.user.event.UserDisabledEvent;
import com.digitalsanctuary.spring.user.event.UserPreDeleteEvent;
import com.digitalsanctuary.spring.user.exceptions.UserAlreadyExistException;
import com.digitalsanctuary.spring.user.persistence.model.PasswordHistoryEntry;
import com.digitalsanctuary.spring.user.persistence.model.PasswordResetToken;
import com.digitalsanctuary.spring.user.persistence.model.Role;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.model.VerificationToken;
import com.digitalsanctuary.spring.user.persistence.repository.PasswordHistoryRepository;
import com.digitalsanctuary.spring.user.persistence.repository.PasswordResetTokenRepository;
import com.digitalsanctuary.spring.user.persistence.repository.RoleRepository;
import com.digitalsanctuary.spring.user.persistence.repository.UserRepository;
import com.digitalsanctuary.spring.user.persistence.repository.VerificationTokenRepository;
import com.digitalsanctuary.spring.user.registration.RegistrationDecision;
import com.digitalsanctuary.spring.user.registration.RegistrationGuard;
import com.digitalsanctuary.spring.user.test.annotations.ServiceTest;
import com.digitalsanctuary.spring.user.test.builders.RoleTestDataBuilder;
import com.digitalsanctuary.spring.user.test.builders.TokenTestDataBuilder;
import com.digitalsanctuary.spring.user.test.builders.UserTestDataBuilder;
import com.digitalsanctuary.spring.user.test.fixtures.TestFixtures;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@ServiceTest
public class UserServiceTest {

    private static final String USER_ROLE_NAME = "ROLE_USER";
    @Mock
    private UserRepository userRepository;
    @Mock
    private VerificationTokenRepository tokenRepository;
    @Mock
    private PasswordResetTokenRepository passwordTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private SessionRegistry sessionRegistry;
    @Mock
    public UserEmailService userEmailService;
    @Mock
    public UserVerificationService userVerificationService;
    @Mock
    private DSUserDetailsService dsUserDetailsService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private PasswordHistoryRepository passwordHistoryRepository;
    @Mock
    private SessionInvalidationService sessionInvalidationService;
    @Mock
    private TokenHasher tokenHasher;
    @Mock
    private RegistrationGuard registrationGuard;
    @InjectMocks
    private UserService userService;
    private User testUser;
    private UserDto testUserDto;

    @BeforeEach
    void setUp() {
        // Use centralized test fixtures for consistent test data
        testUser = TestFixtures.Users.standardUser();
        testUserDto = TestFixtures.DTOs.validUserRegistration();

        // The public entry methods (registerNewUserAccount/changeUserPassword/setInitialPassword) run
        // with NO transaction so bcrypt never holds a DB connection, then delegate the DB write to a
        // @Transactional persist method invoked through the Spring proxy (the "self" reference). Under
        // @InjectMocks there is no proxy and "self" is null, so wire it back to the unit-under-test so
        // the real persist logic executes during these unit tests.
        ReflectionTestUtils.setField(userService, "self", userService);

        // The RegistrationGuard is now enforced inside the registration entry points. Default it to
        // allow so existing registration tests are unaffected; guard-denial behavior is exercised by
        // the dedicated UserServiceRegistrationGuardTest.
        org.mockito.Mockito.lenient().when(registrationGuard.evaluate(any()))
                .thenReturn(RegistrationDecision.allow());
    }

    @Test
    void registerNewUserAccount_returnsUserWhenUserIsNew() {
        // Given
        Role userRole = RoleTestDataBuilder.aUserRole().build();
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(roleRepository.findByName(USER_ROLE_NAME)).thenReturn(userRole);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Set sendRegistrationVerificationEmail to true to test disabled user creation
        ReflectionTestUtils.setField(userService, "sendRegistrationVerificationEmail", true);

        // When
        User saved = userService.registerNewUserAccount(testUserDto);

        // Then
        assertThat(saved).isNotNull();
        assertThat(saved.getEmail()).isEqualTo(testUserDto.getEmail());
        assertThat(saved.getFirstName()).isEqualTo(testUserDto.getFirstName());
        assertThat(saved.getLastName()).isEqualTo(testUserDto.getLastName());
        assertThat(saved.isEnabled()).isFalse(); // New users should not be enabled until verified
        verify(userRepository).save(any(User.class));
    }

    @Test
    void registerNewUserAccount_throwsExceptionWhenUserExist() {
        // Given
        when(userRepository.findByEmail(testUser.getEmail())).thenReturn(testUser);

        // When & Then
        assertThatThrownBy(() -> userService.registerNewUserAccount(testUserDto))
                .isInstanceOf(UserAlreadyExistException.class)
                .hasMessageContaining("There is an account with that email address");
    }

    @Test
    @DisplayName("registerNewUserAccount - translates DataIntegrityViolationException from save into UserAlreadyExistException")
    void registerNewUserAccount_translatesDataIntegrityViolationToUserAlreadyExist() {
        // Given: pre-check passes (email not found) but the concurrent insert loses the race at commit
        Role userRole = RoleTestDataBuilder.aUserRole().build();
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(roleRepository.findByName(USER_ROLE_NAME)).thenReturn(userRole);
        when(userRepository.findByEmail(anyString())).thenReturn(null);
        when(userRepository.save(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("unique constraint violation"));

        // When & Then
        assertThatThrownBy(() -> userService.registerNewUserAccount(testUserDto))
                .isInstanceOf(UserAlreadyExistException.class)
                .hasMessageContaining("There is an account with that email address");
    }

    @Test
    @DisplayName("registerNewUserAccount - translates serialization failure (ConcurrencyFailureException) into UserAlreadyExistException")
    void registerNewUserAccount_translatesConcurrencyFailureToUserAlreadyExist() {
        // Given: pre-check passes but the SERIALIZABLE transaction cannot acquire the lock at commit
        Role userRole = RoleTestDataBuilder.aUserRole().build();
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(roleRepository.findByName(USER_ROLE_NAME)).thenReturn(userRole);
        when(userRepository.findByEmail(anyString())).thenReturn(null);
        when(userRepository.save(any(User.class)))
                .thenThrow(new CannotAcquireLockException("could not serialize access"));

        // When & Then
        assertThatThrownBy(() -> userService.registerNewUserAccount(testUserDto))
                .isInstanceOf(UserAlreadyExistException.class)
                .hasMessageContaining("There is an account with that email address");
    }

    @Test
    @DisplayName("registerNewUserAccount - does not swallow unrelated runtime exceptions from save")
    void registerNewUserAccount_doesNotSwallowUnrelatedExceptions() {
        // Given
        Role userRole = RoleTestDataBuilder.aUserRole().build();
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(roleRepository.findByName(USER_ROLE_NAME)).thenReturn(userRole);
        when(userRepository.findByEmail(anyString())).thenReturn(null);
        when(userRepository.save(any(User.class)))
                .thenThrow(new IllegalStateException("unrelated failure"));

        // When & Then: the unrelated exception must propagate, not be translated to 409
        assertThatThrownBy(() -> userService.registerNewUserAccount(testUserDto))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unrelated failure");
    }

    @Test
    void findByEmail_returnsUserWhenEmailExist() {
        // Given
        when(userRepository.findByEmail(testUser.getEmail())).thenReturn(testUser);

        // When
        User found = userService.findUserByEmail(testUser.getEmail());

        // Then
        assertThat(found).isEqualTo(testUser);
    }

    @Test
    void checkIfValidOldPassword_returnTrueIfValid() {
        // Given
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        // When
        boolean isValid = userService.checkIfValidOldPassword(testUser, testUser.getPassword());

        // Then
        assertThat(isValid).isTrue();
    }

    @Test
    void checkIfValidOldPassword_returnFalseIfInvalid() {
        // Given
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        // When
        boolean isValid = userService.checkIfValidOldPassword(testUser, "wrongPassword");

        // Then
        assertThat(isValid).isFalse();
    }

    @Test
    void changeUserPassword_encodesAndSavesNewPassword() {
        // Given
        String newPassword = "newTestPassword";
        String encodedPassword = "encodedNewPassword";
        when(passwordEncoder.encode(newPassword)).thenReturn(encodedPassword);
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // When
        userService.changeUserPassword(testUser, newPassword);

        // Then
        assertThat(testUser.getPassword()).isEqualTo(encodedPassword);
        verify(passwordEncoder).encode(newPassword);
        verify(userRepository).save(testUser);
    }

    @Test
    void changeUserPassword_invalidatesExistingSessions() {
        // Given
        String newPassword = "newTestPassword";
        when(passwordEncoder.encode(newPassword)).thenReturn("encodedNewPassword");
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // When
        userService.changeUserPassword(testUser, newPassword);

        // Then
        verify(sessionInvalidationService).invalidateSessionsAfterPasswordChange(testUser);
    }

    // Additional tests for comprehensive coverage
    @Test
    @DisplayName("saveRegisteredUser - saves and returns user")
    void saveRegisteredUser_savesAndReturnsUser() {
        // Given
        when(userRepository.save(testUser)).thenReturn(testUser);

        // When
        User savedUser = userService.saveRegisteredUser(testUser);

        // Then
        assertThat(savedUser).isEqualTo(testUser);
        verify(userRepository).save(testUser);
    }

    @Nested
    @DisplayName("User Deletion Tests")
    class UserDeletionTests {

        @Test
        @DisplayName("deleteOrDisableUser - when actuallyDeleteAccount is true - deletes user and tokens")
        void deleteOrDisableUser_whenActuallyDeleteTrue_deletesUserAndTokens() {
            // Given
            ReflectionTestUtils.setField(userService, "actuallyDeleteAccount", true);
            VerificationToken verificationToken = TokenTestDataBuilder.aVerificationToken().forUser(testUser).build();
            PasswordResetToken passwordToken = TokenTestDataBuilder.aPasswordResetToken().forUser(testUser).build();

            when(tokenRepository.findByUser(testUser)).thenReturn(verificationToken);
            when(passwordTokenRepository.findByUser(testUser)).thenReturn(passwordToken);

            // When
            userService.deleteOrDisableUser(testUser);

            // Then
            verify(eventPublisher).publishEvent(any(UserPreDeleteEvent.class));
            verify(tokenRepository).delete(verificationToken);
            verify(passwordTokenRepository).delete(passwordToken);
            verify(userRepository).delete(testUser);
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("deleteOrDisableUser - when actuallyDeleteAccount is false - disables user and publishes UserDisabledEvent")
        void deleteOrDisableUser_whenActuallyDeleteFalse_disablesUser() {
            // Given: no active transaction, so the disable event is published immediately (fallback path)
            ReflectionTestUtils.setField(userService, "actuallyDeleteAccount", false);
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            // When
            userService.deleteOrDisableUser(testUser);

            // Then
            assertThat(testUser.isEnabled()).isFalse();
            verify(userRepository).save(testUser);
            verify(userRepository, never()).delete(any());

            // The soft-delete path is now observable via UserDisabledEvent.
            ArgumentCaptor<UserDisabledEvent> captor = ArgumentCaptor.forClass(UserDisabledEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());
            assertThat(captor.getValue().getUserId()).isEqualTo(testUser.getId());
            assertThat(captor.getValue().getUserEmail()).isEqualTo(testUser.getEmail());
            // No delete-path events should be published on the disable branch.
            verify(eventPublisher, never()).publishEvent(any(UserPreDeleteEvent.class));
            verify(eventPublisher, never()).publishEvent(any(UserDeletedEvent.class));
        }

        @Test
        @DisplayName("deleteOrDisableUser - UserDisabledEvent is deferred until after transaction commit")
        void deleteOrDisableUser_publishesUserDisabledEventAfterCommit() {
            // Given: an active transaction synchronization (simulating the surrounding @Transactional)
            ReflectionTestUtils.setField(userService, "actuallyDeleteAccount", false);
            when(userRepository.save(any(User.class))).thenReturn(testUser);
            TransactionSynchronizationManager.initSynchronization();
            try {
                // When
                userService.deleteOrDisableUser(testUser);

                // Then: neither the disable event nor session revocation may happen before commit. Revoking sessions
                // pre-commit leaves a race window (SUF-03): a login landing between the session scan and the commit
                // registers a new session against the still-enabled row and, because DSUserDetails caches the enabled
                // flag at login, that session would survive the disable.
                verify(eventPublisher, never()).publishEvent(any(UserDisabledEvent.class));
                verify(sessionInvalidationService, never()).invalidateUserSessions(any());

                // Two after-commit synchronizations were registered: session revocation and the disable event.
                List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
                assertThat(syncs).hasSize(2);

                // When the transaction commits, sessions are revoked and the disable event is delivered
                syncs.forEach(TransactionSynchronization::afterCommit);

                // Then
                verify(sessionInvalidationService).invalidateUserSessions(testUser);
                ArgumentCaptor<UserDisabledEvent> captor = ArgumentCaptor.forClass(UserDisabledEvent.class);
                verify(eventPublisher).publishEvent(captor.capture());
                assertThat(captor.getValue().getUserId()).isEqualTo(testUser.getId());
                assertThat(captor.getValue().getUserEmail()).isEqualTo(testUser.getEmail());
            } finally {
                TransactionSynchronizationManager.clearSynchronization();
            }
        }

        @Test
        @DisplayName("deleteOrDisableUser - UserDeletedEvent is deferred until after transaction commit")
        void deleteOrDisableUser_publishesUserDeletedEventAfterCommit() {
            // Given: an active transaction synchronization (simulating the surrounding @Transactional)
            ReflectionTestUtils.setField(userService, "actuallyDeleteAccount", true);
            TransactionSynchronizationManager.initSynchronization();
            try {
                // When
                userService.deleteOrDisableUser(testUser);

                // Then: the pre-delete event fires immediately, but the deleted event must NOT yet, and sessions must
                // NOT be revoked before commit. Pre-commit revocation leaves a race window (SUF-03): a login landing
                // between the session scan and the commit registers a new session against the still-present row that
                // would survive the delete.
                verify(eventPublisher).publishEvent(any(UserPreDeleteEvent.class));
                verify(eventPublisher, never()).publishEvent(any(UserDeletedEvent.class));
                verify(sessionInvalidationService, never()).invalidateUserSessions(any());

                // Two after-commit synchronizations were registered: session revocation and the deleted event.
                List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
                assertThat(syncs).hasSize(2);

                // When the transaction commits, sessions are revoked and the deleted event is delivered
                syncs.forEach(TransactionSynchronization::afterCommit);

                // Then
                verify(sessionInvalidationService).invalidateUserSessions(testUser);
                ArgumentCaptor<UserDeletedEvent> captor = ArgumentCaptor.forClass(UserDeletedEvent.class);
                verify(eventPublisher).publishEvent(captor.capture());
                assertThat(captor.getValue().getUserId()).isEqualTo(testUser.getId());
                assertThat(captor.getValue().getUserEmail()).isEqualTo(testUser.getEmail());
            } finally {
                TransactionSynchronizationManager.clearSynchronization();
            }
        }

        @Test
        @DisplayName("deleteOrDisableUser - UserDeletedEvent still fires when no transaction is active")
        void deleteOrDisableUser_publishesUserDeletedEventWhenNoTransaction() {
            // Given: no active transaction synchronization
            ReflectionTestUtils.setField(userService, "actuallyDeleteAccount", true);

            // When
            userService.deleteOrDisableUser(testUser);

            // Then: the deleted event is published immediately (fallback path)
            verify(eventPublisher).publishEvent(any(UserDeletedEvent.class));
        }

        @Test
        @DisplayName("deleteOrDisableUser - publishes UserPreDeleteEvent when actually deleting")
        void deleteOrDisableUser_publishesUserPreDeleteEvent() {
            // Given
            ReflectionTestUtils.setField(userService, "actuallyDeleteAccount", true);
            ArgumentCaptor<UserPreDeleteEvent> eventCaptor = ArgumentCaptor.forClass(UserPreDeleteEvent.class);

            // When
            userService.deleteOrDisableUser(testUser);

            // Then
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            UserPreDeleteEvent publishedEvent = eventCaptor.getValue();
            assertThat(publishedEvent.getUserId()).isEqualTo(testUser.getId());
            assertThat(publishedEvent.getUserEmail()).isEqualTo(testUser.getEmail());
        }

        @Test
        @DisplayName("deleteOrDisableUser - hard delete revokes all of the user's sessions")
        void deleteOrDisableUser_whenActuallyDeleteTrue_invalidatesAllUserSessions() {
            // Given
            ReflectionTestUtils.setField(userService, "actuallyDeleteAccount", true);

            // When
            userService.deleteOrDisableUser(testUser);

            // Then: every session for the user must be revoked, not just the caller's current request,
            // otherwise a concurrent session keeps carrying the now-deleted principal until it expires.
            verify(sessionInvalidationService).invalidateUserSessions(testUser);
        }

        @Test
        @DisplayName("deleteOrDisableUser - soft disable revokes all of the user's sessions")
        void deleteOrDisableUser_whenActuallyDeleteFalse_invalidatesAllUserSessions() {
            // Given
            ReflectionTestUtils.setField(userService, "actuallyDeleteAccount", false);
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            // When
            userService.deleteOrDisableUser(testUser);

            // Then: a disabled account's other sessions must not remain authenticated on cached authorities.
            verify(sessionInvalidationService).invalidateUserSessions(testUser);
        }
    }

    @Test
    @DisplayName("registerNewUserAccount - enables user when verification email disabled")
    void registerNewUserAccount_enablesUserWhenVerificationDisabled() {
        // Given
        Role userRole = RoleTestDataBuilder.aUserRole().build();
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(roleRepository.findByName(USER_ROLE_NAME)).thenReturn(userRole);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReflectionTestUtils.setField(userService, "sendRegistrationVerificationEmail", false);

        // When
        User saved = userService.registerNewUserAccount(testUserDto);

        // Then
        assertThat(saved.isEnabled()).isTrue();
    }

    @Nested
    @DisplayName("Password Reset Token Tests")
    class PasswordResetTokenTests {

        @BeforeEach
        void stubHasher() {
            // These tests stub findByToken with the raw token string. The service hashes the token
            // before lookup (dual-read), so make the hasher identity here to keep the existing
            // stubs valid. The hashing behavior itself is covered by TokenHashingSecurityTest.
            org.mockito.Mockito.lenient().when(tokenHasher.hash(anyString()))
                    .thenAnswer(invocation -> invocation.getArgument(0));
        }

        @Test
        @DisplayName("getPasswordResetToken - returns token when exists")
        void getPasswordResetToken_returnsTokenWhenExists() {
            // Given
            String tokenString = "test-token-123";
            PasswordResetToken token = TokenTestDataBuilder.aPasswordResetToken().withToken(tokenString)
                    .forUser(testUser).build();
            when(passwordTokenRepository.findByToken(tokenString)).thenReturn(token);

            // When
            PasswordResetToken result = userService.getPasswordResetToken(tokenString);

            // Then
            assertThat(result).isEqualTo(token);
            assertThat(result.getToken()).isEqualTo(tokenString);
            assertThat(result.getUser()).isEqualTo(testUser);
        }

        @Test
        @DisplayName("getUserByPasswordResetToken - returns user when token valid")
        void getUserByPasswordResetToken_returnsUserWhenTokenValid() {
            // Given
            String tokenString = "valid-token";
            PasswordResetToken token = TokenTestDataBuilder.aPasswordResetToken().withToken(tokenString)
                    .forUser(testUser).build();
            when(passwordTokenRepository.findByToken(tokenString)).thenReturn(token);

            // When
            Optional<User> result = userService.getUserByPasswordResetToken(tokenString);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(testUser);
        }

        @Test
        @DisplayName("validatePasswordResetToken - returns VALID for fresh token")
        void validatePasswordResetToken_returnsValidForFreshToken() {
            // Given
            String tokenString = "fresh-token";
            PasswordResetToken token = TokenTestDataBuilder.aValidPasswordResetToken().withToken(tokenString)
                    .expiringInHours(1).build();
            when(passwordTokenRepository.findByToken(tokenString)).thenReturn(token);

            // When
            UserService.TokenValidationResult result = userService.validatePasswordResetToken(tokenString);

            // Then
            assertThat(result).isEqualTo(UserService.TokenValidationResult.VALID);
            verify(passwordTokenRepository, never()).delete(any());
        }

        @Test
        @DisplayName("validatePasswordResetToken - returns INVALID_TOKEN for null token")
        void validatePasswordResetToken_returnsInvalidForNullToken() {
            // Given
            String tokenString = "non-existent";
            when(passwordTokenRepository.findByToken(tokenString)).thenReturn(null);

            // When
            UserService.TokenValidationResult result = userService.validatePasswordResetToken(tokenString);

            // Then
            assertThat(result).isEqualTo(UserService.TokenValidationResult.INVALID_TOKEN);
        }

        @Test
        @DisplayName("validatePasswordResetToken - returns EXPIRED and deletes expired token")
        void validatePasswordResetToken_returnsExpiredForOldToken() {
            // Given
            String tokenString = "expired-token";
            PasswordResetToken token = TokenTestDataBuilder.anExpiredPasswordResetToken().withToken(tokenString)
                    .expiredMinutesAgo(120).build();
            when(passwordTokenRepository.findByToken(tokenString)).thenReturn(token);

            // When
            UserService.TokenValidationResult result = userService.validatePasswordResetToken(tokenString);

            // Then
            assertThat(result).isEqualTo(UserService.TokenValidationResult.EXPIRED);
            verify(passwordTokenRepository).delete(token);
        }
    }

    @Nested
    @DisplayName("User Retrieval Tests")
    class UserRetrievalTests {

        @Test
        @DisplayName("findUserByID - returns user when exists")
        void findUserByID_returnsUserWhenExists() {
            // Given
            Long userId = 123L;
            when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

            // When
            Optional<User> result = userService.findUserByID(userId);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(testUser);
        }

        @Test
        @DisplayName("findUserByID - returns empty when not exists")
        void findUserByID_returnsEmptyWhenNotExists() {
            // Given
            Long userId = 999L;
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            // When
            Optional<User> result = userService.findUserByID(userId);

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("Session Registry Tests")
    class SessionRegistryTests {

        @Test
        @DisplayName("getUsersFromSessionRegistry - returns active user emails")
        void getUsersFromSessionRegistry_returnsActiveUserEmails() {
            // Given
            User user1 = UserTestDataBuilder.aUser().withEmail("user1@example.com").build();
            User user2 = UserTestDataBuilder.aUser().withEmail("user2@example.com").build();
            String stringPrincipal = "string-principal";

            List<Object> principals = Arrays.asList(user1, user2, stringPrincipal);

            when(sessionRegistry.getAllPrincipals()).thenReturn(principals);
            when(sessionRegistry.getAllSessions(user1, false))
                    .thenReturn(Arrays.asList(mock(SessionInformation.class)));
            when(sessionRegistry.getAllSessions(user2, false))
                    .thenReturn(Arrays.asList(mock(SessionInformation.class)));
            when(sessionRegistry.getAllSessions(stringPrincipal, false))
                    .thenReturn(Arrays.asList(mock(SessionInformation.class)));

            // When
            List<String> result = userService.getUsersFromSessionRegistry();

            // Then
            assertThat(result).containsExactlyInAnyOrder("user1@example.com", "user2@example.com", "string-principal");
        }

        @Test
        @DisplayName("getUsersFromSessionRegistry - filters out inactive sessions")
        void getUsersFromSessionRegistry_filtersOutInactiveSessions() {
            // Given
            User activeUser = UserTestDataBuilder.aUser().withEmail("active@example.com").build();
            User inactiveUser = UserTestDataBuilder.aUser().withEmail("inactive@example.com").build();

            when(sessionRegistry.getAllPrincipals()).thenReturn(Arrays.asList(activeUser, inactiveUser));
            when(sessionRegistry.getAllSessions(activeUser, false))
                    .thenReturn(Arrays.asList(mock(SessionInformation.class)));
            when(sessionRegistry.getAllSessions(inactiveUser, false)).thenReturn(Collections.emptyList());

            // When
            List<String> result = userService.getUsersFromSessionRegistry();

            // Then
            assertThat(result).containsExactly("active@example.com");
        }
    }

    @Nested
    @DisplayName("Authentication Without Password Tests")
    class AuthWithoutPasswordTests {

        @Test
        @DisplayName("authWithoutPassword - authenticates valid user")
        void authWithoutPassword_authenticatesValidUser() {
            // Given
            Collection<? extends GrantedAuthority> authorities = Arrays.asList(new SimpleGrantedAuthority("ROLE_USER"));
            // Authorities now come from the principal loaded by loadUserByUsername (which resolves roles/privileges
            // via the entity-graph finder), not from re-deriving them from the possibly-detached incoming user.
            DSUserDetails userDetails = new DSUserDetails(testUser, authorities);

            when(dsUserDetailsService.loadUserByUsername(testUser.getEmail())).thenReturn(userDetails);

            HttpServletRequest mockRequest = mock(HttpServletRequest.class);
            HttpSession mockSession = mock(HttpSession.class);
            ServletRequestAttributes attrs = mock(ServletRequestAttributes.class);

            when(attrs.getRequest()).thenReturn(mockRequest);
            when(mockRequest.getSession(true)).thenReturn(mockSession);

            try (MockedStatic<RequestContextHolder> mockedHolder = mockStatic(RequestContextHolder.class);
                    MockedStatic<SecurityContextHolder> mockedSecurityHolder = mockStatic(
                            SecurityContextHolder.class)) {

                mockedHolder.when(RequestContextHolder::getRequestAttributes).thenReturn(attrs);
                SecurityContext securityContext = mock(SecurityContext.class);
                mockedSecurityHolder.when(SecurityContextHolder::getContext).thenReturn(securityContext);

                // Use thenAnswer to capture and store the authentication set
                final Authentication[] storedAuth = new Authentication[1];
                org.mockito.Mockito.doAnswer(invocation -> {
                    storedAuth[0] = invocation.getArgument(0);
                    return null;
                }).when(securityContext).setAuthentication(any());

                when(securityContext.getAuthentication()).thenAnswer(invocation -> storedAuth[0]);

                // When
                userService.authWithoutPassword(testUser);

                // Then
                verify(securityContext)
                        .setAuthentication(argThat(auth -> auth.getPrincipal().equals(userDetails)
                                && auth.getAuthorities().equals(authorities)));
                verify(mockSession).setAttribute(eq("SPRING_SECURITY_CONTEXT"), eq(securityContext));
            }
        }

        @Test
        @DisplayName("authWithoutPassword - publishes InteractiveAuthenticationSuccessEvent")
        void shouldPublishInteractiveAuthenticationSuccessEventWhenAuthSucceeds() {
            // Given
            Collection<? extends GrantedAuthority> authorities = Arrays.asList(new SimpleGrantedAuthority("ROLE_USER"));
            DSUserDetails userDetails = new DSUserDetails(testUser, authorities);

            when(dsUserDetailsService.loadUserByUsername(testUser.getEmail())).thenReturn(userDetails);

            HttpServletRequest mockRequest = mock(HttpServletRequest.class);
            HttpSession mockSession = mock(HttpSession.class);
            ServletRequestAttributes attrs = mock(ServletRequestAttributes.class);

            when(attrs.getRequest()).thenReturn(mockRequest);
            when(mockRequest.getSession(true)).thenReturn(mockSession);

            try (MockedStatic<RequestContextHolder> mockedHolder = mockStatic(RequestContextHolder.class);
                    MockedStatic<SecurityContextHolder> mockedSecurityHolder = mockStatic(
                            SecurityContextHolder.class)) {

                mockedHolder.when(RequestContextHolder::getRequestAttributes).thenReturn(attrs);
                SecurityContext securityContext = mock(SecurityContext.class);
                mockedSecurityHolder.when(SecurityContextHolder::getContext).thenReturn(securityContext);

                // Return the authentication that was set
                final Authentication[] storedAuth = new Authentication[1];
                org.mockito.Mockito.doAnswer(invocation -> {
                    storedAuth[0] = invocation.getArgument(0);
                    return null;
                }).when(securityContext).setAuthentication(any());
                when(securityContext.getAuthentication()).thenAnswer(invocation -> storedAuth[0]);

                // When
                userService.authWithoutPassword(testUser);

                // Then
                ArgumentCaptor<InteractiveAuthenticationSuccessEvent> eventCaptor =
                        ArgumentCaptor.forClass(InteractiveAuthenticationSuccessEvent.class);
                verify(eventPublisher).publishEvent(eventCaptor.capture());

                InteractiveAuthenticationSuccessEvent event = eventCaptor.getValue();
                assertThat(event).isNotNull();
                assertThat(event.getAuthentication().getPrincipal()).isEqualTo(userDetails);
            }
        }

        @Test
        @DisplayName("authWithoutPassword - handles null user")
        void authWithoutPassword_handlesNullUser() {
            // When
            userService.authWithoutPassword(null);

            // Then
            verify(dsUserDetailsService, never()).loadUserByUsername(any());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("authWithoutPassword - handles user with null email")
        void authWithoutPassword_handlesUserWithNullEmail() {
            // Given
            User userWithNullEmail = UserTestDataBuilder.aUser().withEmail(null).build();

            // When
            userService.authWithoutPassword(userWithNullEmail);

            // Then
            verify(dsUserDetailsService, never()).loadUserByUsername(any());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("authWithoutPassword - handles user not found")
        void authWithoutPassword_handlesUserNotFound() {
            // Given
            when(dsUserDetailsService.loadUserByUsername(testUser.getEmail()))
                    .thenThrow(new UsernameNotFoundException("User not found"));

            // When
            userService.authWithoutPassword(testUser);

            // Then
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("authWithoutPassword - handles no request context")
        void authWithoutPassword_handlesNoRequestContext() {
            // Given
            Collection<? extends GrantedAuthority> authorities = Arrays.asList(new SimpleGrantedAuthority("ROLE_USER"));
            DSUserDetails userDetails = new DSUserDetails(testUser, authorities);

            when(dsUserDetailsService.loadUserByUsername(testUser.getEmail())).thenReturn(userDetails);

            try (MockedStatic<RequestContextHolder> mockedHolder = mockStatic(RequestContextHolder.class);
                    MockedStatic<SecurityContextHolder> mockedSecurityHolder = mockStatic(
                            SecurityContextHolder.class)) {

                mockedHolder.when(RequestContextHolder::getRequestAttributes).thenReturn(null);
                SecurityContext securityContext = mock(SecurityContext.class);
                mockedSecurityHolder.when(SecurityContextHolder::getContext).thenReturn(securityContext);

                // When
                userService.authWithoutPassword(testUser);

                // Then
                verify(securityContext).setAuthentication(any());
                // Should not throw exception even when request context is null
            }
        }

        @Test
        @DisplayName("authWithoutPassword - rotates the session id to defend against session fixation")
        void shouldRotateSessionIdWhenAuthSucceeds() {
            // Given a real request/session bound to the RequestContextHolder so that the servlet
            // changeSessionId() contract is exercised faithfully (MockHttpServletRequest rotates the
            // underlying MockHttpSession id while preserving attributes).
            Collection<? extends GrantedAuthority> authorities = Arrays.asList(new SimpleGrantedAuthority("ROLE_USER"));
            DSUserDetails userDetails = new DSUserDetails(testUser, authorities);

            when(dsUserDetailsService.loadUserByUsername(testUser.getEmail())).thenReturn(userDetails);

            MockHttpServletRequest mockRequest = new MockHttpServletRequest();
            // Ensure a pre-auth session exists with a fixed id and a pre-existing attribute
            HttpSession preAuthSession = mockRequest.getSession(true);
            preAuthSession.setAttribute("preAuthAttr", "value");
            String preAuthSessionId = preAuthSession.getId();
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(mockRequest));

            try {
                // When
                userService.authWithoutPassword(testUser);

                // Then - the session id must have rotated (fixation defense)...
                HttpSession postAuthSession = mockRequest.getSession(false);
                assertThat(postAuthSession).isNotNull();
                assertThat(postAuthSession.getId())
                        .as("session id should change after programmatic login")
                        .isNotEqualTo(preAuthSessionId);
                // ...while preserving existing session attributes...
                assertThat(postAuthSession.getAttribute("preAuthAttr")).isEqualTo("value");
                // ...and the security context must be stored on the (rotated) session.
                assertThat(postAuthSession.getAttribute("SPRING_SECURITY_CONTEXT")).isNotNull();
            } finally {
                RequestContextHolder.resetRequestAttributes();
                SecurityContextHolder.clearContext();
            }
        }
    }
    @Nested
    @DisplayName("Password Status Tests")
    class PasswordStatusTests {

        @Test
        @DisplayName("shouldReturnTrueWhenUserHasPassword")
        void shouldReturnTrueWhenUserHasPassword() {
            // Given
            testUser.setPassword("encodedPassword");

            // When
            boolean result = userService.hasPassword(testUser);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("shouldReturnFalseWhenPasswordNull")
        void shouldReturnFalseWhenPasswordNull() {
            // Given
            testUser.setPassword(null);

            // When
            boolean result = userService.hasPassword(testUser);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("shouldReturnFalseWhenPasswordEmpty")
        void shouldReturnFalseWhenPasswordEmpty() {
            // Given
            testUser.setPassword("");

            // When
            boolean result = userService.hasPassword(testUser);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("shouldReturnFalseForValidOldPasswordWhenPasswordNull")
        void shouldReturnFalseForValidOldPasswordWhenPasswordNull() {
            // Given
            testUser.setPassword(null);

            // When
            boolean result = userService.checkIfValidOldPassword(testUser, "anyPassword");

            // Then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("Password Removal Tests")
    class PasswordRemovalTests {

        @Test
        @DisplayName("shouldRemovePasswordAndClearHistory")
        void shouldRemovePasswordAndClearHistory() {
            // Given
            testUser.setPassword("encodedPassword");
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            userService.removeUserPassword(testUser);

            // Then
            assertThat(testUser.getPassword()).isNull();
            verify(userRepository).save(testUser);
            verify(passwordHistoryRepository).deleteByUser(testUser);
            verify(sessionInvalidationService).invalidateSessionsAfterPasswordChange(testUser);
        }
    }

    @Nested
    @DisplayName("Set Initial Password Tests")
    class SetInitialPasswordTests {

        @Test
        @DisplayName("shouldSetInitialPasswordWhenNoPassword")
        void shouldSetInitialPasswordWhenNoPassword() {
            // Given
            testUser.setPassword(null);
            String rawPassword = "NewSecurePassword123!";
            String encodedPassword = "encodedNewPassword";
            when(passwordEncoder.encode(rawPassword)).thenReturn(encodedPassword);
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            userService.setInitialPassword(testUser, rawPassword);

            // Then
            assertThat(testUser.getPassword()).isEqualTo(encodedPassword);
            verify(passwordEncoder).encode(rawPassword);
            verify(userRepository).save(testUser);
            verify(passwordHistoryRepository).save(any(PasswordHistoryEntry.class));
        }

        @Test
        @DisplayName("shouldThrowWhenUserAlreadyHasPassword")
        void shouldThrowWhenUserAlreadyHasPassword() {
            // Given
            testUser.setPassword("existingPassword");

            // When & Then
            assertThatThrownBy(() -> userService.setInitialPassword(testUser, "newPassword"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already has a password");
        }
    }

    @Nested
    @DisplayName("Passwordless Registration Tests")
    class PasswordlessRegistrationTests {

        @Test
        @DisplayName("shouldRegisterPasswordlessAccountSuccessfully")
        void shouldRegisterPasswordlessAccountSuccessfully() {
            // Given
            PasswordlessRegistrationDto dto = new PasswordlessRegistrationDto();
            dto.setFirstName("Test");
            dto.setLastName("User");
            dto.setEmail("passwordless@example.com");

            Role userRole = RoleTestDataBuilder.aUserRole().build();
            when(roleRepository.findByName(USER_ROLE_NAME)).thenReturn(userRole);
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ReflectionTestUtils.setField(userService, "sendRegistrationVerificationEmail", false);

            // When
            User saved = userService.registerPasswordlessAccount(dto);

            // Then
            assertThat(saved).isNotNull();
            assertThat(saved.getEmail()).isEqualTo("passwordless@example.com");
            assertThat(saved.getFirstName()).isEqualTo("Test");
            assertThat(saved.getLastName()).isEqualTo("User");
            assertThat(saved.getPassword()).isNull();
            assertThat(saved.isEnabled()).isTrue();
            verify(userRepository).save(any(User.class));
            verify(passwordEncoder, never()).encode(anyString());
        }

        @Test
        @DisplayName("shouldThrowWhenEmailExists")
        void shouldThrowWhenEmailExists() {
            // Given
            PasswordlessRegistrationDto dto = new PasswordlessRegistrationDto();
            dto.setFirstName("Test");
            dto.setLastName("User");
            dto.setEmail(testUser.getEmail());

            when(userRepository.findByEmail(testUser.getEmail())).thenReturn(testUser);

            // When & Then
            assertThatThrownBy(() -> userService.registerPasswordlessAccount(dto))
                    .isInstanceOf(UserAlreadyExistException.class)
                    .hasMessageContaining("There is an account with that email address");
        }
    }

    @Nested
    @DisplayName("Password Hashing Outside Transaction Tests")
    class PasswordHashingOutsideTransactionTests {

        /**
         * bcrypt is deliberately slow, so it must run BEFORE the connection-holding DB write. Since the
         * encode now happens in the non-transactional public entry method and the save happens in the
         * proxied @Transactional persist method, asserting that {@code passwordEncoder.encode(...)}
         * fires strictly before {@code userRepository.save(...)} proves the hash is computed outside the
         * transactional persistence step.
         */
        @Test
        @DisplayName("registerNewUserAccount - encodes password BEFORE the persisting save (hash outside the DB write)")
        void registerNewUserAccount_encodesBeforeSave() {
            // Given
            Role userRole = RoleTestDataBuilder.aUserRole().build();
            when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
            when(roleRepository.findByName(USER_ROLE_NAME)).thenReturn(userRole);
            when(userRepository.findByEmail(anyString())).thenReturn(null);
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            userService.registerNewUserAccount(testUserDto);

            // Then: encode runs before the repository save (hash computed outside the persist step)
            InOrder inOrder = inOrder(passwordEncoder, userRepository);
            inOrder.verify(passwordEncoder).encode(anyString());
            inOrder.verify(userRepository).save(any(User.class));
        }

        /**
         * The transactional persist method must receive an ALREADY-encoded password: it does no
         * encoding itself, confirming the (slow) hash happened in the non-transactional caller. We also
         * assert the saved entity carries the encoded value, not the raw password.
         */
        @Test
        @DisplayName("registerNewUserAccount - the persisted user carries the already-encoded password; persist does not re-encode")
        void registerNewUserAccount_persistReceivesEncodedPassword() {
            // Given
            Role userRole = RoleTestDataBuilder.aUserRole().build();
            when(passwordEncoder.encode(testUserDto.getPassword())).thenReturn("encodedPassword");
            when(roleRepository.findByName(USER_ROLE_NAME)).thenReturn(userRole);
            when(userRepository.findByEmail(anyString())).thenReturn(null);
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            userService.registerNewUserAccount(testUserDto);

            // Then: the entity handed to save already holds the encoded password (not the raw value),
            // and encode was invoked exactly once (only in the non-transactional entry method).
            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getPassword()).isEqualTo("encodedPassword");
            verify(passwordEncoder).encode(testUserDto.getPassword());
        }

        @Test
        @DisplayName("changeUserPassword - encodes password BEFORE the persisting save (hash outside the DB write)")
        void changeUserPassword_encodesBeforeSave() {
            // Given
            String newPassword = "newTestPassword";
            when(passwordEncoder.encode(newPassword)).thenReturn("encodedNewPassword");
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            // When
            userService.changeUserPassword(testUser, newPassword);

            // Then: encode runs before save, and session invalidation still happens (in the persist).
            InOrder inOrder = inOrder(passwordEncoder, userRepository, sessionInvalidationService);
            inOrder.verify(passwordEncoder).encode(newPassword);
            inOrder.verify(userRepository).save(testUser);
            inOrder.verify(sessionInvalidationService).invalidateSessionsAfterPasswordChange(testUser);
        }

        @Test
        @DisplayName("setInitialPassword - encodes password BEFORE the persisting save (hash outside the DB write)")
        void setInitialPassword_encodesBeforeSave() {
            // Given
            testUser.setPassword(null);
            String rawPassword = "NewSecurePassword123!";
            when(passwordEncoder.encode(rawPassword)).thenReturn("encodedNewPassword");
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            userService.setInitialPassword(testUser, rawPassword);

            // Then
            InOrder inOrder = inOrder(passwordEncoder, userRepository);
            inOrder.verify(passwordEncoder).encode(rawPassword);
            inOrder.verify(userRepository).save(testUser);
        }
    }

    // Tests temporarily disabled until OAuth2 dependency issue is resolved
    // @Test
    // void checkIfValidOldPassword_returnFalseIfInvalid() {
    // when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);
    // Assertions.assertFalse(userService.checkIfValidOldPassword(testUser,
    // "wrongPassword"));
    // }
    //
    // @Test
    // void changeUserPassword_encodesAndSavesNewPassword() {
    // String newPassword = "newTestPassword";
    // String encodedPassword = "encodedNewPassword";
    //
    // when(passwordEncoder.encode(newPassword)).thenReturn(encodedPassword);
    // when(userRepository.save(any(User.class))).thenReturn(testUser);
    //
    // userService.changeUserPassword(testUser, newPassword);
    //
    // Assertions.assertEquals(encodedPassword, testUser.getPassword());
    // }

}
