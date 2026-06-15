package com.digitalsanctuary.spring.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
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
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import com.digitalsanctuary.spring.user.fixtures.OidcUserTestDataBuilder;
import com.digitalsanctuary.spring.user.persistence.model.Role;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.repository.RoleRepository;
import com.digitalsanctuary.spring.user.persistence.repository.UserRepository;
import com.digitalsanctuary.spring.user.registration.RegistrationDeniedException;
import com.digitalsanctuary.spring.user.registration.RegistrationSource;

/**
 * Verifies that {@link DSOidcUserService} enforces the centralized {@link com.digitalsanctuary.spring.user.registration.RegistrationGuard}
 * (via {@link UserService#enforceRegistrationGuard}) on first-time OIDC registration only, and translates a
 * {@link RegistrationDeniedException} into the same {@code registration_denied} {@link OAuth2AuthenticationException}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DSOidcUserService RegistrationGuard Tests")
class DSOidcUserServiceRegistrationGuardTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private LoginHelperService loginHelperService;

    @Mock
    private UserService userService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

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

        when(userRepository.findWithRolesByEmail("new@company.com")).thenReturn(null);
        doThrow(new RegistrationDeniedException("Organization not whitelisted"))
                .when(userService).enforceRegistrationGuard(eq("new@company.com"), eq(RegistrationSource.OIDC), anyString());

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

        when(userRepository.findWithRolesByEmail("allowed@company.com")).thenReturn(null);
        doNothing().when(userService)
                .enforceRegistrationGuard(eq("allowed@company.com"), eq(RegistrationSource.OIDC), anyString());
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

        when(userRepository.findWithRolesByEmail("existing@company.com")).thenReturn(existingUser);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = service.handleOidcLoginSuccess("keycloak", keycloakUser);

        assertThat(result).isNotNull();
        verifyNoInteractions(userService);
    }
}
