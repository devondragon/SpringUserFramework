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
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import com.digitalsanctuary.spring.user.fixtures.OidcUserTestDataBuilder;
import com.digitalsanctuary.spring.user.persistence.model.Role;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.repository.RoleRepository;
import com.digitalsanctuary.spring.user.persistence.repository.UserRepository;
import com.digitalsanctuary.spring.user.registration.RegistrationContext;
import com.digitalsanctuary.spring.user.registration.RegistrationDecision;
import com.digitalsanctuary.spring.user.registration.RegistrationGuard;

@ExtendWith(MockitoExtension.class)
@DisplayName("DSOidcUserService RegistrationGuard Tests")
class DSOidcUserServiceRegistrationGuardTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private RegistrationGuard registrationGuard;

    @InjectMocks
    private DSOidcUserService service;

    private Role userRole;

    @BeforeEach
    void setUp() {
        userRole = new Role();
        userRole.setName("ROLE_USER");
        userRole.setId(1L);
        lenient().when(roleRepository.findByName("ROLE_USER")).thenReturn(userRole);
    }

    @Test
    @DisplayName("Should reject new OIDC user when guard denies")
    void shouldRejectNewOidcUserWhenGuardDenies() {
        OidcUser keycloakUser = OidcUserTestDataBuilder.keycloak()
                .withEmail("new@company.com")
                .withGivenName("New")
                .withFamilyName("User")
                .build();

        when(userRepository.findByEmail("new@company.com")).thenReturn(null);
        when(registrationGuard.evaluate(any(RegistrationContext.class)))
                .thenReturn(RegistrationDecision.deny("Organization not whitelisted"));

        assertThatThrownBy(() -> service.handleOidcLoginSuccess("keycloak", keycloakUser))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("Organization not whitelisted")
                .satisfies(ex -> {
                    OAuth2AuthenticationException oauthEx = (OAuth2AuthenticationException) ex;
                    assertThat(oauthEx.getError().getErrorCode()).isEqualTo("registration_denied");
                });
    }

    @Test
    @DisplayName("Should allow new OIDC user when guard allows")
    void shouldAllowNewOidcUserWhenGuardAllows() {
        OidcUser keycloakUser = OidcUserTestDataBuilder.keycloak()
                .withEmail("allowed@company.com")
                .withGivenName("Allowed")
                .withFamilyName("User")
                .build();

        when(userRepository.findByEmail("allowed@company.com")).thenReturn(null);
        when(registrationGuard.evaluate(any(RegistrationContext.class)))
                .thenReturn(RegistrationDecision.allow());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = service.handleOidcLoginSuccess("keycloak", keycloakUser);

        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo("allowed@company.com");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("Should not call guard for existing OIDC user")
    void shouldNotCallGuardForExistingOidcUser() {
        OidcUser keycloakUser = OidcUserTestDataBuilder.keycloak()
                .withEmail("existing@company.com")
                .withGivenName("Existing")
                .withFamilyName("User")
                .build();

        User existingUser = new User();
        existingUser.setId(100L);
        existingUser.setEmail("existing@company.com");
        existingUser.setProvider(User.Provider.KEYCLOAK);

        when(userRepository.findByEmail("existing@company.com")).thenReturn(existingUser);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = service.handleOidcLoginSuccess("keycloak", keycloakUser);

        assertThat(result).isNotNull();
        verifyNoInteractions(registrationGuard);
    }
}
