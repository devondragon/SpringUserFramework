package com.digitalsanctuary.spring.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.digitalsanctuary.spring.user.dto.UserDto;
import com.digitalsanctuary.spring.user.event.UserPreDeleteEvent;
import com.digitalsanctuary.spring.user.exceptions.UserAlreadyExistException;
import com.digitalsanctuary.spring.user.persistence.model.PasswordResetToken;
import com.digitalsanctuary.spring.user.persistence.model.Role;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.model.VerificationToken;
import com.digitalsanctuary.spring.user.persistence.repository.PasswordHistoryRepository;
import com.digitalsanctuary.spring.user.persistence.repository.PasswordResetTokenRepository;
import com.digitalsanctuary.spring.user.persistence.repository.RoleRepository;
import com.digitalsanctuary.spring.user.persistence.repository.UserRepository;
import com.digitalsanctuary.spring.user.persistence.repository.VerificationTokenRepository;
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
    private AuthorityService authorityService;

    @InjectMocks
    @Mock
    private PasswordHistoryRepository passwordHistoryRepository;
    private UserService userService;
    private User testUser;
    private UserDto testUserDto;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setEmail("test@example.com");
        testUser.setFirstName("testFirstName");
        testUser.setLastName("testLastName");
        testUser.setPassword("testPassword");
        testUser.setRoles(Collections.singletonList(new Role("ROLE_USER")));
        testUser.setEnabled(true);

        testUserDto = new UserDto();
        testUserDto.setEmail("test@example.com");
        testUserDto.setFirstName("testFirstName");
        testUserDto.setLastName("testLastName");
        testUserDto.setPassword("testPassword");
        testUserDto.setRole(1);

        // Use centralized test fixtures for consistent test data
        testUser = TestFixtures.Users.standardUser();
        testUserDto = TestFixtures.DTOs.validUserRegistration();

        userService = new UserService(userRepository, tokenRepository, passwordTokenRepository, passwordEncoder,
                roleRepository, sessionRegistry,
                userEmailService, userVerificationService, authorityService, dsUserDetailsService, eventPublisher,
                passwordHistoryRepository);
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
        assertThatThrownBy(() -> userService.registerNewUserAccount(testUserDto)).isInstanceOf(UserAlreadyExistException.class)
                .hasMessageContaining("There is an account with that email address");
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
        @DisplayName("deleteOrDisableUser - when actuallyDeleteAccount is false - disables user")
        void deleteOrDisableUser_whenActuallyDeleteFalse_disablesUser() {
            // Given
            ReflectionTestUtils.setField(userService, "actuallyDeleteAccount", false);
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            // When
            userService.deleteOrDisableUser(testUser);

            // Then
            assertThat(testUser.isEnabled()).isFalse();
            verify(userRepository).save(testUser);
            verify(userRepository, never()).delete(any());
            verify(eventPublisher, never()).publishEvent(any());
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
            assertThat(publishedEvent.getUser()).isEqualTo(testUser);
            assertThat(publishedEvent.getUserId()).isEqualTo(testUser.getId());
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

        @Test
        @DisplayName("getPasswordResetToken - returns token when exists")
        void getPasswordResetToken_returnsTokenWhenExists() {
            // Given
            String tokenString = "test-token-123";
            PasswordResetToken token = TokenTestDataBuilder.aPasswordResetToken().withToken(tokenString).forUser(testUser).build();
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
            PasswordResetToken token = TokenTestDataBuilder.aPasswordResetToken().withToken(tokenString).forUser(testUser).build();
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
            PasswordResetToken token = TokenTestDataBuilder.aValidPasswordResetToken().withToken(tokenString).expiringInHours(1).build();
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
            PasswordResetToken token = TokenTestDataBuilder.anExpiredPasswordResetToken().withToken(tokenString).expiredMinutesAgo(120).build();
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
            when(sessionRegistry.getAllSessions(user1, false)).thenReturn(Arrays.asList(mock(SessionInformation.class)));
            when(sessionRegistry.getAllSessions(user2, false)).thenReturn(Arrays.asList(mock(SessionInformation.class)));
            when(sessionRegistry.getAllSessions(stringPrincipal, false)).thenReturn(Arrays.asList(mock(SessionInformation.class)));

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
            when(sessionRegistry.getAllSessions(activeUser, false)).thenReturn(Arrays.asList(mock(SessionInformation.class)));
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
            DSUserDetails userDetails = new DSUserDetails(testUser);
            Collection<? extends GrantedAuthority> authorities = Arrays.asList(new SimpleGrantedAuthority("ROLE_USER"));

            when(dsUserDetailsService.loadUserByUsername(testUser.getEmail())).thenReturn(userDetails);
            when(authorityService.getAuthoritiesFromUser(testUser)).thenReturn((Collection) authorities);

            HttpServletRequest mockRequest = mock(HttpServletRequest.class);
            HttpSession mockSession = mock(HttpSession.class);
            ServletRequestAttributes attrs = mock(ServletRequestAttributes.class);

            when(attrs.getRequest()).thenReturn(mockRequest);
            when(mockRequest.getSession(true)).thenReturn(mockSession);

            try (MockedStatic<RequestContextHolder> mockedHolder = mockStatic(RequestContextHolder.class);
                    MockedStatic<SecurityContextHolder> mockedSecurityHolder = mockStatic(SecurityContextHolder.class)) {

                mockedHolder.when(RequestContextHolder::getRequestAttributes).thenReturn(attrs);
                SecurityContext securityContext = mock(SecurityContext.class);
                mockedSecurityHolder.when(SecurityContextHolder::getContext).thenReturn(securityContext);

                // When
                userService.authWithoutPassword(testUser);

                // Then
                verify(securityContext)
                        .setAuthentication(argThat(auth -> auth.getPrincipal().equals(userDetails) && auth.getAuthorities().equals(authorities)));
                verify(mockSession).setAttribute(eq("SPRING_SECURITY_CONTEXT"), eq(securityContext));
            }
        }

        @Test
        @DisplayName("authWithoutPassword - handles null user")
        void authWithoutPassword_handlesNullUser() {
            // When
            userService.authWithoutPassword(null);

            // Then
            verify(dsUserDetailsService, never()).loadUserByUsername(any());
            verify(authorityService, never()).getAuthoritiesFromUser(any());
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
            verify(authorityService, never()).getAuthoritiesFromUser(any());
        }

        @Test
        @DisplayName("authWithoutPassword - handles user not found")
        void authWithoutPassword_handlesUserNotFound() {
            // Given
            when(dsUserDetailsService.loadUserByUsername(testUser.getEmail())).thenThrow(new UsernameNotFoundException("User not found"));

            // When
            userService.authWithoutPassword(testUser);

            // Then
            verify(authorityService, never()).getAuthoritiesFromUser(any());
        }

        @Test
        @DisplayName("authWithoutPassword - handles no request context")
        void authWithoutPassword_handlesNoRequestContext() {
            // Given
            DSUserDetails userDetails = new DSUserDetails(testUser);
            Collection<? extends GrantedAuthority> authorities = Arrays.asList(new SimpleGrantedAuthority("ROLE_USER"));

            when(dsUserDetailsService.loadUserByUsername(testUser.getEmail())).thenReturn(userDetails);
            when(authorityService.getAuthoritiesFromUser(testUser)).thenReturn((Collection) authorities);

            try (MockedStatic<RequestContextHolder> mockedHolder = mockStatic(RequestContextHolder.class);
                    MockedStatic<SecurityContextHolder> mockedSecurityHolder = mockStatic(SecurityContextHolder.class)) {

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
