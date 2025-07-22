package com.digitalsanctuary.spring.user.test.config;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * OAuth2 test configuration that provides mock OAuth2/OIDC services for testing. This configuration eliminates the need for external OAuth2 providers
 * during tests.
 */
@TestConfiguration
@Profile("test")
public class OAuth2TestConfiguration {

    /**
     * Test client registration repository with pre-configured OAuth2 clients.
     */
    @Bean
    @Primary
    public ClientRegistrationRepository testClientRegistrationRepository() {
        return new InMemoryClientRegistrationRepository(testGoogleClientRegistration(), testGitHubClientRegistration(), testOidcClientRegistration());
    }

    /**
     * Mock OAuth2 user service that returns test users.
     */
    @Bean
    @Primary
    public OAuth2UserService<OAuth2UserRequest, OAuth2User> mockOAuth2UserService() {
        return new MockOAuth2UserService();
    }

    /**
     * Mock OIDC user service that returns test OIDC users.
     */
    @Bean
    @Primary
    public OidcUserService mockOidcUserService() {
        return new MockOidcUserService();
    }

    /**
     * Google client registration for testing.
     */
    private ClientRegistration testGoogleClientRegistration() {
        return ClientRegistration.withRegistrationId("google").clientId("test-google-client-id").clientSecret("test-google-client-secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email").authorizationUri("http://localhost:8080/oauth2/authorize")
                .tokenUri("http://localhost:8080/oauth2/token").userInfoUri("http://localhost:8080/oauth2/userinfo")
                .userNameAttributeName(IdTokenClaimNames.SUB).jwkSetUri("http://localhost:8080/oauth2/jwks").clientName("Google").build();
    }

    /**
     * GitHub client registration for testing.
     */
    private ClientRegistration testGitHubClientRegistration() {
        return ClientRegistration.withRegistrationId("github").clientId("test-github-client-id").clientSecret("test-github-client-secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("read:user", "user:email").authorizationUri("http://localhost:8080/oauth2/authorize")
                .tokenUri("http://localhost:8080/oauth2/token").userInfoUri("http://localhost:8080/oauth2/user").userNameAttributeName("id")
                .clientName("GitHub").build();
    }

    /**
     * Generic OIDC client registration for testing.
     */
    private ClientRegistration testOidcClientRegistration() {
        return ClientRegistration.withRegistrationId("oidc").clientId("test-oidc-client-id").clientSecret("test-oidc-client-secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email").authorizationUri("http://localhost:8080/oauth2/authorize")
                .tokenUri("http://localhost:8080/oauth2/token").userInfoUri("http://localhost:8080/oauth2/userinfo")
                .userNameAttributeName(IdTokenClaimNames.SUB).jwkSetUri("http://localhost:8080/oauth2/jwks").clientName("Test OIDC Provider").build();
    }

    /**
     * Mock OAuth2UserService for testing.
     */
    public static class MockOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

        @Override
        public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
            String registrationId = userRequest.getClientRegistration().getRegistrationId();

            Map<String, Object> attributes = new HashMap<>();

            if ("google".equals(registrationId)) {
                attributes.put("sub", "google-123456");
                attributes.put("name", "Google Test User");
                attributes.put("email", "google.user@test.com");
                attributes.put("email_verified", true);
                attributes.put("picture", "https://example.com/picture.jpg");
            } else if ("github".equals(registrationId)) {
                attributes.put("id", "github-789012");
                attributes.put("login", "githubuser");
                attributes.put("name", "GitHub Test User");
                attributes.put("email", "github.user@test.com");
                attributes.put("avatar_url", "https://example.com/avatar.jpg");
            } else {
                attributes.put("sub", "oidc-345678");
                attributes.put("name", "OIDC Test User");
                attributes.put("email", "oidc.user@test.com");
                attributes.put("email_verified", true);
            }

            return new DefaultOAuth2User(Arrays.asList(() -> "ROLE_USER"), attributes,
                    userRequest.getClientRegistration().getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName());
        }
    }

    /**
     * Mock OidcUserService for testing.
     */
    public static class MockOidcUserService extends OidcUserService {

        @Override
        public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
            String registrationId = userRequest.getClientRegistration().getRegistrationId();

            Map<String, Object> claims = new HashMap<>();
            claims.put(IdTokenClaimNames.SUB, "oidc-" + registrationId + "-123456");
            claims.put("name", "OIDC Test User");
            claims.put("email", registrationId + ".oidc@test.com");
            claims.put("email_verified", true);
            claims.put(IdTokenClaimNames.ISS, "http://localhost:8080");
            claims.put(IdTokenClaimNames.AUD, Arrays.asList("test-oidc-client-id"));
            claims.put(IdTokenClaimNames.IAT, Instant.now());
            claims.put(IdTokenClaimNames.EXP, Instant.now().plusSeconds(3600));

            OidcIdToken idToken = new OidcIdToken("test-id-token", Instant.now(), Instant.now().plusSeconds(3600), claims);

            OidcUserInfo userInfo = new OidcUserInfo(claims);

            return new DefaultOidcUser(Arrays.asList(() -> "ROLE_USER"), idToken, userInfo);
        }
    }

    /**
     * Helper class to create OAuth2 authentication tokens for testing.
     */
    @Bean
    public OAuth2TestTokenFactory oAuth2TestTokenFactory() {
        return new OAuth2TestTokenFactory();
    }

    /**
     * Factory for creating test OAuth2 tokens.
     */
    public static class OAuth2TestTokenFactory {

        /**
         * Creates a test OAuth2 access token.
         */
        public OAuth2AccessToken createAccessToken() {
            return new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "test-access-token", Instant.now(), Instant.now().plusSeconds(3600),
                    new HashSet<>(Arrays.asList("read", "write")));
        }

        /**
         * Creates a test OAuth2 user with custom attributes.
         */
        public OAuth2User createOAuth2User(String email, String name, String providerId) {
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("sub", providerId);
            attributes.put("name", name);
            attributes.put("email", email);
            attributes.put("email_verified", true);

            return new DefaultOAuth2User(Arrays.asList(() -> "ROLE_USER"), attributes, "sub");
        }
    }
}
