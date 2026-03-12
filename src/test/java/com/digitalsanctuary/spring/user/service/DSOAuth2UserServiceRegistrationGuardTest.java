package com.digitalsanctuary.spring.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;

import com.digitalsanctuary.spring.user.fixtures.OAuth2UserTestDataBuilder;
import com.digitalsanctuary.spring.user.persistence.model.Role;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.repository.RoleRepository;
import com.digitalsanctuary.spring.user.persistence.repository.UserRepository;
import com.digitalsanctuary.spring.user.registration.RegistrationContext;
import com.digitalsanctuary.spring.user.registration.RegistrationDecision;
import com.digitalsanctuary.spring.user.registration.RegistrationGuard;

@ExtendWith(MockitoExtension.class)
@DisplayName("DSOAuth2UserService RegistrationGuard Tests")
class DSOAuth2UserServiceRegistrationGuardTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private LoginHelperService loginHelperService;

    @Mock
    private RegistrationGuard registrationGuard;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private DSOAuth2UserService service;

    private Role userRole;

    @BeforeEach
    void setUp() {
        userRole = new Role();
        userRole.setName("ROLE_USER");
        userRole.setId(1L);
        lenient().when(roleRepository.findByName("ROLE_USER")).thenReturn(userRole);
    }

    @Test
    @DisplayName("Should reject new OAuth2 user when guard denies")
    void shouldRejectNewOAuth2UserWhenGuardDenies() {
        OAuth2User googleUser = OAuth2UserTestDataBuilder.google()
                .withEmail("new@gmail.com")
                .withFirstName("New")
                .withLastName("User")
                .build();

        when(userRepository.findByEmail("new@gmail.com")).thenReturn(null);
        when(registrationGuard.evaluate(any(RegistrationContext.class)))
                .thenReturn(RegistrationDecision.deny("Domain not allowed"));

        assertThatThrownBy(() -> service.handleOAuthLoginSuccess("google", googleUser))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("Domain not allowed")
                .satisfies(ex -> {
                    OAuth2AuthenticationException oauthEx = (OAuth2AuthenticationException) ex;
                    assertThat(oauthEx.getError().getErrorCode()).isEqualTo("registration_denied");
                });
    }

    @Test
    @DisplayName("Should allow new OAuth2 user when guard allows")
    void shouldAllowNewOAuth2UserWhenGuardAllows() {
        OAuth2User googleUser = OAuth2UserTestDataBuilder.google()
                .withEmail("allowed@gmail.com")
                .withFirstName("Allowed")
                .withLastName("User")
                .build();

        when(userRepository.findByEmail("allowed@gmail.com")).thenReturn(null);
        when(registrationGuard.evaluate(any(RegistrationContext.class)))
                .thenReturn(RegistrationDecision.allow());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = service.handleOAuthLoginSuccess("google", googleUser);

        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo("allowed@gmail.com");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("Should not call guard for existing OAuth2 user")
    void shouldNotCallGuardForExistingOAuth2User() {
        OAuth2User googleUser = OAuth2UserTestDataBuilder.google()
                .withEmail("existing@gmail.com")
                .withFirstName("Existing")
                .withLastName("User")
                .build();

        User existingUser = new User();
        existingUser.setId(100L);
        existingUser.setEmail("existing@gmail.com");
        existingUser.setProvider(User.Provider.GOOGLE);

        when(userRepository.findByEmail("existing@gmail.com")).thenReturn(existingUser);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = service.handleOAuthLoginSuccess("google", googleUser);

        assertThat(result).isNotNull();
        verifyNoInteractions(registrationGuard);
    }
}
