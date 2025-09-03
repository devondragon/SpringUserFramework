package com.digitalsanctuary.spring.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import com.digitalsanctuary.spring.user.fixtures.OidcUserTestDataBuilder;
import com.digitalsanctuary.spring.user.persistence.model.Role;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.repository.RoleRepository;
import com.digitalsanctuary.spring.user.persistence.repository.UserRepository;

/**
 * Comprehensive unit tests for DSOidcUserService that verify actual business logic
 * for OIDC authentication flows, particularly Keycloak integration.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DSOidcUserService Tests")
class DSOidcUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

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

    @Nested
    @DisplayName("Keycloak OIDC Tests")
    class KeycloakOidcTests {

        @Test
        @DisplayName("Should create new user from Keycloak OIDC data")
        void shouldCreateNewUserFromKeycloakOidc() {
            // Given
            OidcUser keycloakUser = OidcUserTestDataBuilder.keycloak()
                .withEmail("john.doe@company.com")
                .withGivenName("John")
                .withFamilyName("Doe")
                .withPreferredUsername("jdoe")
                .build();
            
            when(userRepository.findByEmail("john.doe@company.com")).thenReturn(null);
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User savedUser = invocation.getArgument(0);
                savedUser.setId(123L);
                return savedUser;
            });

            // When
            User result = service.handleOidcLoginSuccess("keycloak", keycloakUser);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getEmail()).isEqualTo("john.doe@company.com");
            assertThat(result.getFirstName()).isEqualTo("John");
            assertThat(result.getLastName()).isEqualTo("Doe");
            assertThat(result.getProvider()).isEqualTo(User.Provider.KEYCLOAK);
            assertThat(result.isEnabled()).isTrue(); // OIDC users are auto-enabled
            assertThat(result.getRoles()).containsExactly(userRole);
            
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("Should extract user info using OIDC standard methods")
        void shouldExtractUserInfoUsingOidcMethods() {
            // Given
            OidcUser keycloakUser = OidcUserTestDataBuilder.keycloak()
                .withEmail("oidc@keycloak.com")
                .withGivenName("OidcFirst")
                .withFamilyName("OidcLast")
                .build();

            // When
            User result = service.getUserFromKeycloakOidc2User(keycloakUser);

            // Then
            assertThat(result).isNotNull();
            // Verify it uses OIDC methods, not getAttribute
            assertThat(result.getEmail()).isEqualTo("oidc@keycloak.com");
            assertThat(result.getFirstName()).isEqualTo("OidcFirst");
            assertThat(result.getLastName()).isEqualTo("OidcLast");
            assertThat(result.getProvider()).isEqualTo(User.Provider.KEYCLOAK);
        }

        @Test
        @DisplayName("Should update existing Keycloak user with new information")
        void shouldUpdateExistingKeycloakUser() {
            // Given
            OidcUser keycloakUser = OidcUserTestDataBuilder.keycloak()
                .withEmail("existing@keycloak.com")
                .withGivenName("UpdatedFirst")
                .withFamilyName("UpdatedLast")
                .build();
            
            User existingUser = new User();
            existingUser.setId(100L);
            existingUser.setEmail("existing@keycloak.com");
            existingUser.setFirstName("OldFirst");
            existingUser.setLastName("OldLast");
            existingUser.setProvider(User.Provider.KEYCLOAK);
            existingUser.setEnabled(true);
            
            when(userRepository.findByEmail("existing@keycloak.com")).thenReturn(existingUser);
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            User result = service.handleOidcLoginSuccess("keycloak", keycloakUser);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(100L); // Same user ID
            assertThat(result.getFirstName()).isEqualTo("UpdatedFirst");
            assertThat(result.getLastName()).isEqualTo("UpdatedLast");
            verify(userRepository).save(existingUser);
        }

        @Test
        @DisplayName("Should handle Keycloak user with minimal claims")
        void shouldHandleKeycloakUserWithMinimalClaims() {
            // Given - Keycloak user with only email
            OidcUser minimalUser = OidcUserTestDataBuilder.keycloak()
                .withEmail("minimal@keycloak.com")
                .withoutUserInfoClaim("given_name")
                .withoutUserInfoClaim("family_name")
                .build();
            
            when(userRepository.findByEmail("minimal@keycloak.com")).thenReturn(null);
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            User result = service.handleOidcLoginSuccess("keycloak", minimalUser);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getEmail()).isEqualTo("minimal@keycloak.com");
            assertThat(result.getFirstName()).isNull();
            assertThat(result.getLastName()).isNull();
            assertThat(result.getProvider()).isEqualTo(User.Provider.KEYCLOAK);
        }

        @Test
        @DisplayName("Should handle null OIDC user info gracefully")
        void shouldHandleNullOidcUserInfo() {
            // Given - OIDC user with null UserInfo
            OidcUser nullInfoUser = OidcUserTestDataBuilder.minimal()
                .withoutUserInfoClaim("email")
                .withoutUserInfoClaim("given_name")
                .withoutUserInfoClaim("family_name")
                .build();
            
            // When
            User result = service.getUserFromKeycloakOidc2User(nullInfoUser);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getEmail()).isNull();
            assertThat(result.getFirstName()).isNull();
            assertThat(result.getLastName()).isNull();
        }
    }

    @Nested
    @DisplayName("Provider Conflict Tests")
    class ProviderConflictTests {

        @Test
        @DisplayName("Should reject Keycloak login when user registered with Google")
        void shouldRejectKeycloakLoginForGoogleUser() {
            // Given
            OidcUser keycloakUser = OidcUserTestDataBuilder.keycloak()
                .withEmail("conflict@example.com")
                .build();
            
            User existingUser = new User();
            existingUser.setEmail("conflict@example.com");
            existingUser.setProvider(User.Provider.GOOGLE);
            
            when(userRepository.findByEmail("conflict@example.com")).thenReturn(existingUser);

            // When/Then
            assertThatThrownBy(() -> service.handleOidcLoginSuccess("keycloak", keycloakUser))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("Looks like you're signed up with your GOOGLE account");
        }

        @Test
        @DisplayName("Should reject OIDC login when user registered locally")
        void shouldRejectOidcLoginForLocalUser() {
            // Given
            OidcUser keycloakUser = OidcUserTestDataBuilder.keycloak()
                .withEmail("local@example.com")
                .build();
            
            User existingUser = new User();
            existingUser.setEmail("local@example.com");
            existingUser.setProvider(User.Provider.LOCAL);
            
            when(userRepository.findByEmail("local@example.com")).thenReturn(existingUser);

            // When/Then
            assertThatThrownBy(() -> service.handleOidcLoginSuccess("keycloak", keycloakUser))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("Looks like you're signed up with your LOCAL account");
        }

        @Test
        @DisplayName("Should allow same provider re-authentication")
        void shouldAllowSameProviderReAuthentication() {
            // Given
            OidcUser keycloakUser = OidcUserTestDataBuilder.keycloak()
                .withEmail("same@keycloak.com")
                .build();
            
            User existingUser = new User();
            existingUser.setEmail("same@keycloak.com");
            existingUser.setProvider(User.Provider.KEYCLOAK);
            existingUser.setFirstName("Existing");
            
            when(userRepository.findByEmail("same@keycloak.com")).thenReturn(existingUser);
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When - Should not throw exception
            User result = service.handleOidcLoginSuccess("keycloak", keycloakUser);

            // Then
            assertThat(result).isNotNull();
            verify(userRepository).save(existingUser);
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should throw exception for unsupported OIDC provider")
        void shouldThrowExceptionForUnsupportedProvider() {
            // Given
            OidcUser unsupportedUser = OidcUserTestDataBuilder.unsupported().build();

            // When/Then
            assertThatThrownBy(() -> service.handleOidcLoginSuccess("okta", unsupportedUser))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("Sorry! Login with okta is not supported yet");
        }

        @Test
        @DisplayName("Should throw exception when OIDC user is null")
        void shouldThrowExceptionWhenOidcUserIsNull() {
            // When/Then
            assertThatThrownBy(() -> service.handleOidcLoginSuccess("keycloak", null))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("Sorry! An error occurred while processing your login request");
        }

        @Test
        @DisplayName("Should handle null principal in Keycloak user extraction")
        void shouldHandleNullPrincipalInKeycloakExtraction() {
            // When
            User result = service.getUserFromKeycloakOidc2User(null);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should handle repository exceptions during save")
        void shouldHandleRepositoryExceptionsDuringSave() {
            // Given
            OidcUser keycloakUser = OidcUserTestDataBuilder.keycloak()
                .withEmail("error@keycloak.com")
                .build();
            
            when(userRepository.findByEmail("error@keycloak.com")).thenReturn(null);
            when(userRepository.save(any(User.class)))
                .thenThrow(new RuntimeException("Database connection failed"));

            // When/Then
            assertThatThrownBy(() -> service.handleOidcLoginSuccess("keycloak", keycloakUser))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Database connection failed");
        }
    }

    @Nested
    @DisplayName("LoadUser Integration Tests")
    class LoadUserIntegrationTests {

        @Test
        @DisplayName("Should load user through OidcUserRequest flow with DSUserDetails")
        void shouldLoadUserThroughOidcRequestFlow() {
            // Given
            ClientRegistration clientRegistration = ClientRegistration.withRegistrationId("keycloak")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .clientId("test-client-id")
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://keycloak.example.com/auth/realms/test/protocol/openid-connect/auth")
                .tokenUri("https://keycloak.example.com/auth/realms/test/protocol/openid-connect/token")
                .userInfoUri("https://keycloak.example.com/auth/realms/test/protocol/openid-connect/userinfo")
                .userNameAttributeName("sub")
                .clientName("Keycloak")
                .scope("openid", "profile", "email")
                .build();
                
            OAuth2AccessToken accessToken = mock(OAuth2AccessToken.class);
            OidcUserRequest userRequest = new OidcUserRequest(clientRegistration, accessToken, 
                OidcUserTestDataBuilder.keycloak().build().getIdToken());
            
            OidcUser keycloakUser = OidcUserTestDataBuilder.keycloak()
                .withEmail("loadtest@keycloak.com")
                .build();
                
            // Create a spy and mock the internal service  
            DSOidcUserService spyService = spy(service);
            spyService.defaultOidcUserService = mock(OidcUserService.class);
            when(spyService.defaultOidcUserService.loadUser(userRequest)).thenReturn(keycloakUser);
            
            when(userRepository.findByEmail("loadtest@keycloak.com")).thenReturn(null);
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                user.setId(999L);
                return user;
            });

            // When
            OidcUser result = spyService.loadUser(userRequest);

            // Then
            assertThat(result).isNotNull();
            assertThat(result).isInstanceOf(DSUserDetails.class);
            DSUserDetails dsUserDetails = (DSUserDetails) result;
            assertThat(dsUserDetails.getIdToken()).isNotNull();
            assertThat(dsUserDetails.getUserInfo()).isNotNull();
            assertThat(dsUserDetails.getAuthorities()).isNotEmpty();
            
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("Should preserve OIDC tokens and authorities in DSUserDetails")
        void shouldPreserveOidcTokensInDSUserDetails() {
            // Given
            OidcUser keycloakUser = OidcUserTestDataBuilder.keycloak()
                .withEmail("tokens@keycloak.com")
                .build();
            
            User dbUser = new User();
            dbUser.setId(123L);
            dbUser.setEmail("tokens@keycloak.com");
            dbUser.setProvider(User.Provider.KEYCLOAK);

            // When
            User extractedUser = service.getUserFromKeycloakOidc2User(keycloakUser);
            
            // Simulate what happens in loadUser
            DSUserDetails dsUserDetails = DSUserDetails.builder()
                .user(dbUser)
                .oidcUserInfo(keycloakUser.getUserInfo())
                .oidcIdToken(keycloakUser.getIdToken())
                .grantedAuthorities(keycloakUser.getAuthorities())
                .build();

            // Then
            assertThat(dsUserDetails.getIdToken()).isEqualTo(keycloakUser.getIdToken());
            assertThat(dsUserDetails.getUserInfo()).isEqualTo(keycloakUser.getUserInfo());
            // Verify authorities are preserved
            assertThat(dsUserDetails.getAuthorities()).isNotEmpty();
            assertThat(dsUserDetails.getAuthorities().size()).isEqualTo(keycloakUser.getAuthorities().size());
        }
    }
}