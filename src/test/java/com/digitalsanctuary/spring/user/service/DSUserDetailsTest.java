package com.digitalsanctuary.spring.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;

import com.digitalsanctuary.spring.user.persistence.model.User;

/**
 * Unit tests for {@link DSUserDetails} attribute handling.
 *
 * <p>Verifies that {@code getAttributes()} satisfies the {@code OAuth2User} contract across all
 * constructor paths: OAuth2, OIDC, and local/password login.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DSUserDetails Tests")
class DSUserDetailsTest {

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setEmail("test@example.com");
        testUser.setFirstName("Test");
        testUser.setLastName("User");
    }

    @Nested
    @DisplayName("Simple Constructor (OAuth2 / Local Login)")
    class SimpleConstructorTests {

        @Test
        @DisplayName("Should return provider attributes when provided")
        void shouldReturnProviderAttributesWhenProvided() {
            Map<String, Object> providerAttrs = new HashMap<>();
            providerAttrs.put("email", "test@example.com");
            providerAttrs.put("given_name", "Test");
            providerAttrs.put("family_name", "User");
            providerAttrs.put("picture", "https://example.com/photo.jpg");
            providerAttrs.put("sub", "123456789");

            DSUserDetails details = new DSUserDetails(testUser,
                    List.of(new SimpleGrantedAuthority("ROLE_USER")), providerAttrs);

            assertThat(details.getAttributes()).isNotNull();
            assertThat(details.getAttributes()).containsEntry("email", "test@example.com");
            assertThat(details.getAttributes()).containsEntry("picture", "https://example.com/photo.jpg");
            assertThat(details.getAttributes()).containsEntry("sub", "123456789");
        }

        @Test
        @DisplayName("Should build fallback attributes from User entity when attributes are null")
        void shouldBuildFallbackAttributesWhenNull() {
            DSUserDetails details = new DSUserDetails(testUser,
                    List.of(new SimpleGrantedAuthority("ROLE_USER")));

            assertThat(details.getAttributes()).isNotNull();
            assertThat(details.getAttributes()).containsEntry("email", "test@example.com");
            assertThat(details.getAttributes()).containsEntry("given_name", "Test");
            assertThat(details.getAttributes()).containsEntry("family_name", "User");
            assertThat(details.getAttributes()).containsEntry("name", "Test User");
        }

        @Test
        @DisplayName("Should return empty map for User with no fields set")
        void shouldReturnEmptyMapForMinimalUser() {
            User minimalUser = new User();

            DSUserDetails details = new DSUserDetails(minimalUser);

            assertThat(details.getAttributes()).isNotNull();
            assertThat(details.getAttributes()).isEmpty();
        }

        @Test
        @DisplayName("Should build name from first name only without 'null' suffix")
        void shouldBuildNameFromFirstNameOnly() {
            User user = new User();
            user.setFirstName("Test");

            DSUserDetails details = new DSUserDetails(user);

            assertThat(details.getAttributes()).containsEntry("given_name", "Test");
            assertThat(details.getAttributes()).containsEntry("name", "Test");
            assertThat((String) details.getAttributes().get("name")).doesNotContain("null");
        }

        @Test
        @DisplayName("Should build name from last name only without 'null' prefix")
        void shouldBuildNameFromLastNameOnly() {
            User user = new User();
            user.setLastName("User");

            DSUserDetails details = new DSUserDetails(user);

            assertThat(details.getAttributes()).containsEntry("family_name", "User");
            assertThat(details.getAttributes()).containsEntry("name", "User");
            assertThat((String) details.getAttributes().get("name")).doesNotContain("null");
        }

        @Test
        @DisplayName("Should return an unmodifiable map from getAttributes()")
        void shouldReturnUnmodifiableAttributes() {
            Map<String, Object> providerAttrs = new HashMap<>();
            providerAttrs.put("email", "test@example.com");

            DSUserDetails details = new DSUserDetails(testUser,
                    List.of(new SimpleGrantedAuthority("ROLE_USER")), providerAttrs);

            assertThat(details.getAttributes()).isUnmodifiable();
        }

        @Test
        @DisplayName("getAttribute('email') should return email for OAuth2 user")
        void shouldSupportGetAttributePattern() {
            Map<String, Object> providerAttrs = Map.of(
                    "email", "oauth@example.com",
                    "sub", "abc123"
            );

            DSUserDetails details = new DSUserDetails(testUser,
                    List.of(new SimpleGrantedAuthority("ROLE_USER")), providerAttrs);

            // This is the standard OAuth2User pattern that was broken before the fix
            assertThat((String) details.getAttribute("email")).isEqualTo("oauth@example.com");
        }

        @Test
        @DisplayName("Should defensively copy provided attributes")
        void shouldDefensivelyCopyAttributes() {
            Map<String, Object> providerAttrs = new HashMap<>();
            providerAttrs.put("email", "test@example.com");

            DSUserDetails details = new DSUserDetails(testUser, null, providerAttrs);

            // Modifying the original map should not affect DSUserDetails
            providerAttrs.put("injected", "value");
            assertThat(details.getAttributes()).doesNotContainKey("injected");
        }
    }

    @Nested
    @DisplayName("OIDC Constructor")
    class OidcConstructorTests {

        private OidcIdToken idToken;
        private OidcUserInfo userInfo;

        @BeforeEach
        void setUp() {
            Map<String, Object> tokenClaims = new HashMap<>();
            tokenClaims.put("sub", "f:12345678:testuser");
            tokenClaims.put("email", "oidc@example.com");
            tokenClaims.put("email_verified", true);
            tokenClaims.put("given_name", "Test");
            tokenClaims.put("family_name", "User");
            tokenClaims.put("iss", "https://idp.example.com");

            idToken = new OidcIdToken("token-value", Instant.now(), Instant.now().plusSeconds(3600), tokenClaims);

            Map<String, Object> infoClaims = new HashMap<>();
            infoClaims.put("sub", "f:12345678:testuser");
            infoClaims.put("email", "oidc@example.com");
            userInfo = new OidcUserInfo(infoClaims);
        }

        @Test
        @DisplayName("Should return provider attributes when provided to OIDC constructor")
        void shouldReturnProviderAttributesWhenProvided() {
            Map<String, Object> providerAttrs = new HashMap<>();
            providerAttrs.put("email", "oidc@example.com");
            providerAttrs.put("custom_claim", "custom_value");

            DSUserDetails details = new DSUserDetails(testUser, userInfo, idToken,
                    List.of(new SimpleGrantedAuthority("ROLE_USER")), providerAttrs);

            assertThat(details.getAttributes()).isNotNull();
            assertThat(details.getAttributes()).containsEntry("email", "oidc@example.com");
            assertThat(details.getAttributes()).containsEntry("custom_claim", "custom_value");
        }

        @Test
        @DisplayName("Should fall back to idToken claims when attributes are null")
        void shouldFallBackToIdTokenClaimsWhenAttributesNull() {
            DSUserDetails details = new DSUserDetails(testUser, userInfo, idToken,
                    List.of(new SimpleGrantedAuthority("ROLE_USER")));

            assertThat(details.getAttributes()).isNotNull();
            assertThat(details.getAttributes()).containsEntry("sub", "f:12345678:testuser");
            assertThat(details.getAttributes()).containsEntry("email", "oidc@example.com");
            assertThat(details.getAttributes()).containsEntry("email_verified", true);
            assertThat(details.getAttributes()).containsEntry("iss", "https://idp.example.com");
        }

        @Test
        @DisplayName("Should fall back to User entity when both attributes and idToken are null")
        void shouldFallBackToUserEntityWhenBothNull() {
            DSUserDetails details = new DSUserDetails(testUser, userInfo, null,
                    List.of(new SimpleGrantedAuthority("ROLE_USER")));

            assertThat(details.getAttributes()).isNotNull();
            assertThat(details.getAttributes()).containsEntry("email", "test@example.com");
            assertThat(details.getAttributes()).containsEntry("given_name", "Test");
        }

        @Test
        @DisplayName("Should never return null from getAttributes() for OIDC path")
        void shouldNeverReturnNullForOidcPath() {
            // All-null OIDC constructor — was previously returning null (NPE risk)
            DSUserDetails details = new DSUserDetails(testUser, (OidcUserInfo) null, (OidcIdToken) null);

            assertThat(details.getAttributes()).isNotNull();
        }

        @Test
        @DisplayName("getAttribute('email') should work for OIDC user")
        void shouldSupportGetAttributePatternForOidc() {
            Map<String, Object> providerAttrs = Map.of("email", "oidc@example.com", "sub", "test-sub");

            DSUserDetails details = new DSUserDetails(testUser, userInfo, idToken,
                    List.of(new SimpleGrantedAuthority("ROLE_USER")), providerAttrs);

            assertThat((String) details.getAttribute("email")).isEqualTo("oidc@example.com");
        }

        @Test
        @DisplayName("Should preserve OIDC tokens alongside attributes")
        void shouldPreserveOidcTokensAlongsideAttributes() {
            Map<String, Object> providerAttrs = Map.of("email", "oidc@example.com");

            DSUserDetails details = new DSUserDetails(testUser, userInfo, idToken,
                    List.of(new SimpleGrantedAuthority("ROLE_USER")), providerAttrs);

            assertThat(details.getIdToken()).isEqualTo(idToken);
            assertThat(details.getUserInfo()).isEqualTo(userInfo);
            assertThat(details.getAttributes()).containsEntry("email", "oidc@example.com");
        }
    }

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("Builder should accept attributes parameter")
        void shouldAcceptAttributesViaBuilder() {
            Map<String, Object> providerAttrs = Map.of("email", "builder@example.com", "sub", "builder-sub");

            DSUserDetails details = DSUserDetails.builder()
                    .user(testUser)
                    .attributes(providerAttrs)
                    .grantedAuthorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                    .build();

            assertThat(details.getAttributes()).containsEntry("email", "builder@example.com");
            assertThat(details.getAttributes()).containsEntry("sub", "builder-sub");
        }

        @Test
        @DisplayName("Builder without attributes should use fallback")
        void shouldUseFallbackWhenBuilderOmitsAttributes() {
            DSUserDetails details = DSUserDetails.builder()
                    .user(testUser)
                    .grantedAuthorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                    .build();

            assertThat(details.getAttributes()).isNotNull();
            assertThat(details.getAttributes()).containsEntry("email", "test@example.com");
        }
    }
}
