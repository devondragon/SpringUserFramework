package com.digitalsanctuary.spring.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.digitalsanctuary.spring.user.dto.PasswordlessRegistrationDto;
import com.digitalsanctuary.spring.user.dto.UserDto;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.repository.PasswordHistoryRepository;
import com.digitalsanctuary.spring.user.persistence.repository.PasswordResetTokenRepository;
import com.digitalsanctuary.spring.user.persistence.repository.RoleRepository;
import com.digitalsanctuary.spring.user.persistence.repository.UserRepository;
import com.digitalsanctuary.spring.user.persistence.repository.VerificationTokenRepository;
import com.digitalsanctuary.spring.user.registration.RegistrationContext;
import com.digitalsanctuary.spring.user.registration.RegistrationDecision;
import com.digitalsanctuary.spring.user.registration.RegistrationDeniedException;
import com.digitalsanctuary.spring.user.registration.RegistrationGuard;
import com.digitalsanctuary.spring.user.registration.RegistrationSource;
import com.digitalsanctuary.spring.user.test.annotations.ServiceTest;

/**
 * Verifies that the {@link com.digitalsanctuary.spring.user.registration.RegistrationGuard} is enforced
 * INSIDE {@link UserService} so that direct callers of the registration methods cannot bypass it (the
 * guard-bypass this task closes), and that the correct {@link RegistrationSource} is supplied per path.
 */
@ServiceTest
class UserServiceRegistrationGuardTest {

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
    private UserEmailService userEmailService;
    @Mock
    private UserVerificationService userVerificationService;
    @Mock
    private DSUserDetailsService dsUserDetailsService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private AuthorityService authorityService;
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

    @BeforeEach
    void setUp() {
        // The public registration entry methods delegate the DB write to a @Transactional persist method
        // invoked through the Spring proxy ("self"). Under @InjectMocks there is no proxy, so wire self
        // back to the unit-under-test.
        ReflectionTestUtils.setField(userService, "self", userService);
    }

    private UserDto formDto() {
        UserDto dto = new UserDto();
        dto.setEmail("blocked@example.com");
        dto.setFirstName("Blocked");
        dto.setLastName("User");
        dto.setPassword("password123");
        dto.setMatchingPassword("password123");
        return dto;
    }

    private PasswordlessRegistrationDto passwordlessDto() {
        PasswordlessRegistrationDto dto = new PasswordlessRegistrationDto();
        dto.setEmail("blocked@example.com");
        dto.setFirstName("Blocked");
        dto.setLastName("User");
        return dto;
    }

    @Test
    @DisplayName("Direct call to registerNewUserAccount is DENIED when guard denies (bypass closed, FORM source)")
    void formRegistrationDeniedWhenGuardDenies() {
        when(registrationGuard.evaluate(any(RegistrationContext.class)))
                .thenReturn(RegistrationDecision.deny("Registration is by invitation only"));

        assertThatThrownBy(() -> userService.registerNewUserAccount(formDto()))
                .isInstanceOf(RegistrationDeniedException.class)
                .hasMessageContaining("Registration is by invitation only");

        // No user was persisted — the bypass is closed.
        verify(userRepository, never()).save(any(User.class));

        // The guard received the correct source (FORM) and email.
        ArgumentCaptor<RegistrationContext> captor = ArgumentCaptor.forClass(RegistrationContext.class);
        verify(registrationGuard).evaluate(captor.capture());
        assertThat(captor.getValue().source()).isEqualTo(RegistrationSource.FORM);
        assertThat(captor.getValue().email()).isEqualTo("blocked@example.com");
    }

    @Test
    @DisplayName("Direct call to registerPasswordlessAccount is DENIED when guard denies (bypass closed, PASSWORDLESS source)")
    void passwordlessRegistrationDeniedWhenGuardDenies() {
        when(registrationGuard.evaluate(any(RegistrationContext.class)))
                .thenReturn(RegistrationDecision.deny("Beta access required"));

        assertThatThrownBy(() -> userService.registerPasswordlessAccount(passwordlessDto()))
                .isInstanceOf(RegistrationDeniedException.class)
                .hasMessageContaining("Beta access required");

        verify(userRepository, never()).save(any(User.class));

        ArgumentCaptor<RegistrationContext> captor = ArgumentCaptor.forClass(RegistrationContext.class);
        verify(registrationGuard).evaluate(captor.capture());
        assertThat(captor.getValue().source()).isEqualTo(RegistrationSource.PASSWORDLESS);
    }

    @Test
    @DisplayName("enforceRegistrationGuard throws RegistrationDeniedException for denied OAuth registration")
    void oauthEnforceThrowsWhenGuardDenies() {
        when(registrationGuard.evaluate(any(RegistrationContext.class)))
                .thenReturn(RegistrationDecision.deny("Domain not allowed"));

        assertThatThrownBy(() ->
                userService.enforceRegistrationGuard("social@example.com", RegistrationSource.OAUTH2, "google"))
                .isInstanceOf(RegistrationDeniedException.class)
                .hasMessageContaining("Domain not allowed");

        ArgumentCaptor<RegistrationContext> captor = ArgumentCaptor.forClass(RegistrationContext.class);
        verify(registrationGuard).evaluate(captor.capture());
        assertThat(captor.getValue().source()).isEqualTo(RegistrationSource.OAUTH2);
        assertThat(captor.getValue().providerName()).isEqualTo("google");
    }

    @Test
    @DisplayName("enforceRegistrationGuard is a no-op when the guard allows")
    void oauthEnforceAllows() {
        when(registrationGuard.evaluate(any(RegistrationContext.class)))
                .thenReturn(RegistrationDecision.allow());

        userService.enforceRegistrationGuard("social@example.com", RegistrationSource.OIDC, "keycloak");

        ArgumentCaptor<RegistrationContext> captor = ArgumentCaptor.forClass(RegistrationContext.class);
        verify(registrationGuard).evaluate(captor.capture());
        assertThat(captor.getValue().source()).isEqualTo(RegistrationSource.OIDC);
    }

    @Test
    @DisplayName("Form registration proceeds to persistence when the guard allows")
    void formRegistrationProceedsWhenGuardAllows() {
        when(registrationGuard.evaluate(any(RegistrationContext.class)))
                .thenReturn(RegistrationDecision.allow());
        when(passwordEncoder.encode(any())).thenReturn("encoded");
        when(roleRepository.findByName(USER_ROLE_NAME))
                .thenReturn(com.digitalsanctuary.spring.user.test.builders.RoleTestDataBuilder.aUserRole().build());
        when(userRepository.findByEmail(any())).thenReturn(null);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User saved = userService.registerNewUserAccount(formDto());

        assertThat(saved).isNotNull();
        verify(userRepository).save(any(User.class));
    }
}
