package com.digitalsanctuary.spring.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import com.digitalsanctuary.spring.user.audit.AuditEvent;
import com.digitalsanctuary.spring.user.fixtures.OAuth2UserTestDataBuilder;
import com.digitalsanctuary.spring.user.persistence.model.Role;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.repository.RoleRepository;
import com.digitalsanctuary.spring.user.persistence.repository.UserRepository;

/**
 * Comprehensive unit tests for DSOAuth2UserService that verify actual business logic,
 * not just mock interactions. Tests cover Google and Facebook OAuth2 flows, error handling,
 * user registration, and updates.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DSOAuth2UserService Tests")
class DSOAuth2UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private LoginHelperService loginHelperService;

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

    @Nested
    @DisplayName("Google OAuth2 Tests")
    class GoogleOAuth2Tests {

        @Test
        @DisplayName("Should create new user from Google OAuth2 data")
        void shouldCreateNewUserFromGoogleOAuth2() {
            // Given
            OAuth2User googleUser = OAuth2UserTestDataBuilder.google()
                .withEmail("john.doe@gmail.com")
                .withFirstName("John")
                .withLastName("Doe")
                .build();
            
            when(userRepository.findByEmail("john.doe@gmail.com")).thenReturn(null);
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User savedUser = invocation.getArgument(0);
                savedUser.setId(123L);
                return savedUser;
            });

            // When
            User result = service.handleOAuthLoginSuccess("google", googleUser);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getEmail()).isEqualTo("john.doe@gmail.com");
            assertThat(result.getFirstName()).isEqualTo("John");
            assertThat(result.getLastName()).isEqualTo("Doe");
            assertThat(result.getProvider()).isEqualTo(User.Provider.GOOGLE);
            assertThat(result.isEnabled()).isTrue(); // OAuth2 users are auto-enabled
            assertThat(result.getRoles()).containsExactly(userRole);

            // Verify audit event was published
            ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            AuditEvent auditEvent = eventCaptor.getValue();
            assertThat(auditEvent.getAction()).isEqualTo("OAuth2 Registration Success");
            assertThat(auditEvent.getActionStatus()).isEqualTo("Success");
            assertThat(auditEvent.getUser().getEmail()).isEqualTo("john.doe@gmail.com");
        }

        @Test
        @DisplayName("Should update existing Google user with new name information")
        void shouldUpdateExistingGoogleUser() {
            // Given
            OAuth2User googleUser = OAuth2UserTestDataBuilder.google()
                .withEmail("existing@gmail.com")
                .withFirstName("UpdatedFirst")
                .withLastName("UpdatedLast")
                .build();
            
            User existingUser = new User();
            existingUser.setId(100L);
            existingUser.setEmail("existing@gmail.com");
            existingUser.setFirstName("OldFirst");
            existingUser.setLastName("OldLast");
            existingUser.setProvider(User.Provider.GOOGLE);
            existingUser.setEnabled(true);
            
            when(userRepository.findByEmail("existing@gmail.com")).thenReturn(existingUser);
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            User result = service.handleOAuthLoginSuccess("google", googleUser);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(100L); // Same user ID
            assertThat(result.getFirstName()).isEqualTo("UpdatedFirst");
            assertThat(result.getLastName()).isEqualTo("UpdatedLast");
            assertThat(result.getEmail()).isEqualTo("existing@gmail.com");
            verify(userRepository).save(existingUser);
            verify(eventPublisher, never()).publishEvent(any()); // No event for updates
        }

        @Test
        @DisplayName("Should handle Google user with missing optional fields")
        void shouldHandleGoogleUserWithMissingFields() {
            // Given - Google user without last name
            OAuth2User googleUser = OAuth2UserTestDataBuilder.google()
                .withEmail("nolastname@gmail.com")
                .withFirstName("SingleName")
                .withoutAttribute("family_name")
                .build();
            
            when(userRepository.findByEmail("nolastname@gmail.com")).thenReturn(null);
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            User result = service.handleOAuthLoginSuccess("google", googleUser);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getEmail()).isEqualTo("nolastname@gmail.com");
            assertThat(result.getFirstName()).isEqualTo("SingleName");
            assertThat(result.getLastName()).isNull();
            assertThat(result.getProvider()).isEqualTo(User.Provider.GOOGLE);
        }

        @Test
        @DisplayName("Should convert email to lowercase for consistency")
        void shouldConvertEmailToLowercase() {
            // Given
            OAuth2User googleUser = OAuth2UserTestDataBuilder.google()
                .withEmail("John.Doe@GMAIL.COM")
                .build();
            
            when(userRepository.findByEmail("john.doe@gmail.com")).thenReturn(null);
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            service.handleOAuthLoginSuccess("google", googleUser);

            // Then
            verify(userRepository).findByEmail("john.doe@gmail.com"); // Lowercase lookup
        }
    }

    @Nested
    @DisplayName("Facebook OAuth2 Tests")
    class FacebookOAuth2Tests {

        @Test
        @DisplayName("Should create new user from Facebook OAuth2 data")
        void shouldCreateNewUserFromFacebookOAuth2() {
            // Given
            OAuth2User facebookUser = OAuth2UserTestDataBuilder.facebook()
                .withEmail("jane.smith@facebook.com")
                .withFullName("Jane Marie Smith")
                .build();
            
            when(userRepository.findByEmail("jane.smith@facebook.com")).thenReturn(null);
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            User result = service.handleOAuthLoginSuccess("facebook", facebookUser);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getEmail()).isEqualTo("jane.smith@facebook.com");
            assertThat(result.getFirstName()).isEqualTo("Jane");
            assertThat(result.getLastName()).isEqualTo("Smith"); // Takes last part of name
            assertThat(result.getProvider()).isEqualTo(User.Provider.FACEBOOK);
            assertThat(result.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("Should parse Facebook full name correctly")
        void shouldParseFacebookFullNameCorrectly() {
            // Given - Test various name formats
            Map<String, String[]> nameTests = new HashMap<>();
            nameTests.put("John Doe", new String[]{"John", "Doe"});
            nameTests.put("Mary Jane Watson", new String[]{"Mary", "Watson"});
            nameTests.put("SingleName", new String[]{"SingleName", null});
            nameTests.put("", new String[]{"", null}); // Empty string creates empty first name

            for (Map.Entry<String, String[]> entry : nameTests.entrySet()) {
                OAuth2User facebookUser = OAuth2UserTestDataBuilder.facebook()
                    .withEmail("test@facebook.com")
                    .withFullName(entry.getKey())
                    .build();

                // When
                User result = service.getUserFromFacebookOAuth2User(facebookUser);

                // Then
                assertThat(result.getFirstName()).isEqualTo(entry.getValue()[0]);
                assertThat(result.getLastName()).isEqualTo(entry.getValue()[1]);
            }
        }

        @Test
        @DisplayName("Should handle Facebook user without name attribute")
        void shouldHandleFacebookUserWithoutName() {
            // Given
            OAuth2User facebookUser = OAuth2UserTestDataBuilder.facebook()
                .withEmail("noname@facebook.com")
                .withoutAttribute("name")
                .build();
            
            when(userRepository.findByEmail("noname@facebook.com")).thenReturn(null);
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            User result = service.handleOAuthLoginSuccess("facebook", facebookUser);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getEmail()).isEqualTo("noname@facebook.com");
            assertThat(result.getFirstName()).isNull();
            assertThat(result.getLastName()).isNull();
        }
    }

    @Nested
    @DisplayName("Provider Conflict Tests")
    class ProviderConflictTests {

        @Test
        @DisplayName("Should reject Google login when user registered with Facebook")
        void shouldRejectGoogleLoginForFacebookUser() {
            // Given
            OAuth2User googleUser = OAuth2UserTestDataBuilder.google()
                .withEmail("conflict@example.com")
                .build();
            
            User existingUser = new User();
            existingUser.setEmail("conflict@example.com");
            existingUser.setProvider(User.Provider.FACEBOOK);
            
            when(userRepository.findByEmail("conflict@example.com")).thenReturn(existingUser);

            // When/Then
            assertThatThrownBy(() -> service.handleOAuthLoginSuccess("google", googleUser))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("Looks like you're signed up with your FACEBOOK account");
        }

        @Test
        @DisplayName("Should reject OAuth2 login when user registered locally")
        void shouldRejectOAuth2LoginForLocalUser() {
            // Given
            OAuth2User googleUser = OAuth2UserTestDataBuilder.google()
                .withEmail("local@example.com")
                .build();
            
            User existingUser = new User();
            existingUser.setEmail("local@example.com");
            existingUser.setProvider(User.Provider.LOCAL);
            
            when(userRepository.findByEmail("local@example.com")).thenReturn(existingUser);

            // When/Then
            assertThatThrownBy(() -> service.handleOAuthLoginSuccess("google", googleUser))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("Looks like you're signed up with your LOCAL account");
        }

        @Test
        @DisplayName("Should allow same provider re-authentication")
        void shouldAllowSameProviderReAuthentication() {
            // Given
            OAuth2User googleUser = OAuth2UserTestDataBuilder.google()
                .withEmail("same@gmail.com")
                .build();
            
            User existingUser = new User();
            existingUser.setEmail("same@gmail.com");
            existingUser.setProvider(User.Provider.GOOGLE);
            existingUser.setFirstName("Existing");
            
            when(userRepository.findByEmail("same@gmail.com")).thenReturn(existingUser);
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When - Should not throw exception
            User result = service.handleOAuthLoginSuccess("google", googleUser);

            // Then
            assertThat(result).isNotNull();
            verify(userRepository).save(existingUser);
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should throw exception for unsupported provider")
        void shouldThrowExceptionForUnsupportedProvider() {
            // Given
            OAuth2User unsupportedUser = OAuth2UserTestDataBuilder.unsupported().build();

            // When/Then
            assertThatThrownBy(() -> service.handleOAuthLoginSuccess("twitter", unsupportedUser))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("Sorry! Login with twitter is not supported yet");
        }

        @Test
        @DisplayName("Should throw exception when OAuth2User is null")
        void shouldThrowExceptionWhenOAuth2UserIsNull() {
            // When/Then
            assertThatThrownBy(() -> service.handleOAuthLoginSuccess("google", null))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("Sorry! An error occurred while processing your login request");
        }

        @Test
        @DisplayName("Should handle null principal in Google user extraction")
        void shouldHandleNullPrincipalInGoogleExtraction() {
            // When
            User result = service.getUserFromGoogleOAuth2User(null);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should handle null principal in Facebook user extraction")
        void shouldHandleNullPrincipalInFacebookExtraction() {
            // When
            User result = service.getUserFromFacebookOAuth2User(null);

            // Then
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("LoadUser Integration Tests")
    class LoadUserIntegrationTests {

        @Test
        @DisplayName("Should load user through OAuth2UserRequest flow")
        void shouldLoadUserThroughOAuth2RequestFlow() {
            // Given
            ClientRegistration clientRegistration = ClientRegistration.withRegistrationId("google")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .clientId("test-client-id")
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .userInfoUri("https://www.googleapis.com/oauth2/v3/userinfo")
                .userNameAttributeName("sub")
                .clientName("Google")
                .build();
                
            OAuth2AccessToken accessToken = mock(OAuth2AccessToken.class);
            OAuth2UserRequest userRequest = new OAuth2UserRequest(clientRegistration, accessToken);
            
            OAuth2User googleUser = OAuth2UserTestDataBuilder.google()
                .withEmail("loadtest@gmail.com")
                .build();
                
            // Create a spy and mock the internal service
            DSOAuth2UserService spyService = spy(service);
            spyService.defaultOAuth2UserService = mock(DefaultOAuth2UserService.class);
            when(spyService.defaultOAuth2UserService.loadUser(userRequest)).thenReturn(googleUser);
            
            DSUserDetails mockUserDetails = mock(DSUserDetails.class);
            when(loginHelperService.userLoginHelper(any(User.class))).thenReturn(mockUserDetails);
            when(userRepository.findByEmail("loadtest@gmail.com")).thenReturn(null);
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            OAuth2User result = spyService.loadUser(userRequest);

            // Then
            assertThat(result).isEqualTo(mockUserDetails);
            verify(loginHelperService).userLoginHelper(any(User.class));
            verify(userRepository).save(any(User.class));
        }
    }
}