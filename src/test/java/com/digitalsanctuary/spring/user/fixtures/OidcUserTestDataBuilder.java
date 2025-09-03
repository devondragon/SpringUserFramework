package com.digitalsanctuary.spring.user.fixtures;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * Test data builder for creating OidcUser instances with realistic Keycloak-specific attributes.
 */
public class OidcUserTestDataBuilder {
    
    private Map<String, Object> idTokenClaims = new HashMap<>();
    private Map<String, Object> userInfoClaims = new HashMap<>();
    private Set<GrantedAuthority> authorities = Set.of(new SimpleGrantedAuthority("ROLE_USER"));
    
    private OidcUserTestDataBuilder() {
        // Private constructor to enforce builder pattern
    }
    
    /**
     * Creates a builder for Keycloak OIDC user with standard Keycloak claims.
     */
    public static OidcUserTestDataBuilder keycloak() {
        OidcUserTestDataBuilder builder = new OidcUserTestDataBuilder();
        
        // Standard OIDC ID token claims
        builder.idTokenClaims.put(IdTokenClaimNames.SUB, "f:12345678-1234-1234-1234-123456789abc:testuser");
        builder.idTokenClaims.put(IdTokenClaimNames.ISS, "https://keycloak.example.com/realms/test-realm");
        builder.idTokenClaims.put(IdTokenClaimNames.AUD, "test-client");
        builder.idTokenClaims.put(IdTokenClaimNames.EXP, Instant.now().plusSeconds(3600));
        builder.idTokenClaims.put(IdTokenClaimNames.IAT, Instant.now());
        builder.idTokenClaims.put(IdTokenClaimNames.AUTH_TIME, Instant.now().minusSeconds(60));
        builder.idTokenClaims.put(IdTokenClaimNames.NONCE, "test-nonce");
        builder.idTokenClaims.put("azp", "test-client");
        builder.idTokenClaims.put("session_state", "test-session-state");
        
        // Standard UserInfo claims
        builder.userInfoClaims.put("sub", "f:12345678-1234-1234-1234-123456789abc:testuser");
        builder.userInfoClaims.put("email", "test.user@keycloak.com");
        builder.userInfoClaims.put("email_verified", true);
        builder.userInfoClaims.put("given_name", "Test");
        builder.userInfoClaims.put("family_name", "User");
        builder.userInfoClaims.put("name", "Test User");
        builder.userInfoClaims.put("preferred_username", "testuser");
        
        return builder;
    }
    
    /**
     * Creates a builder for an unsupported OIDC provider.
     */
    public static OidcUserTestDataBuilder unsupported() {
        OidcUserTestDataBuilder builder = new OidcUserTestDataBuilder();
        
        builder.idTokenClaims.put(IdTokenClaimNames.SUB, "unknown-provider-user");
        builder.idTokenClaims.put(IdTokenClaimNames.ISS, "https://unknown.provider.com");
        builder.idTokenClaims.put(IdTokenClaimNames.AUD, "unknown-client");
        builder.idTokenClaims.put(IdTokenClaimNames.EXP, Instant.now().plusSeconds(3600));
        builder.idTokenClaims.put(IdTokenClaimNames.IAT, Instant.now());
        
        builder.userInfoClaims.put("sub", "unknown-provider-user");
        builder.userInfoClaims.put("email", "test@unknown.com");
        
        return builder;
    }
    
    /**
     * Creates a builder with minimal claims (missing some standard fields).
     */
    public static OidcUserTestDataBuilder minimal() {
        OidcUserTestDataBuilder builder = new OidcUserTestDataBuilder();
        
        builder.idTokenClaims.put(IdTokenClaimNames.SUB, "minimal-user");
        builder.idTokenClaims.put(IdTokenClaimNames.ISS, "https://minimal.provider.com");
        builder.idTokenClaims.put(IdTokenClaimNames.AUD, "minimal-client");
        builder.idTokenClaims.put(IdTokenClaimNames.EXP, Instant.now().plusSeconds(3600));
        builder.idTokenClaims.put(IdTokenClaimNames.IAT, Instant.now());
        
        // Minimal userInfo - missing names
        builder.userInfoClaims.put("sub", "minimal-user");
        
        return builder;
    }
    
    public OidcUserTestDataBuilder withEmail(String email) {
        this.userInfoClaims.put("email", email);
        return this;
    }
    
    public OidcUserTestDataBuilder withGivenName(String givenName) {
        this.userInfoClaims.put("given_name", givenName);
        return this;
    }
    
    public OidcUserTestDataBuilder withFamilyName(String familyName) {
        this.userInfoClaims.put("family_name", familyName);
        return this;
    }
    
    public OidcUserTestDataBuilder withFullName(String fullName) {
        this.userInfoClaims.put("name", fullName);
        return this;
    }
    
    public OidcUserTestDataBuilder withPreferredUsername(String username) {
        this.userInfoClaims.put("preferred_username", username);
        return this;
    }
    
    public OidcUserTestDataBuilder withIdTokenClaim(String key, Object value) {
        this.idTokenClaims.put(key, value);
        return this;
    }
    
    public OidcUserTestDataBuilder withUserInfoClaim(String key, Object value) {
        this.userInfoClaims.put(key, value);
        return this;
    }
    
    public OidcUserTestDataBuilder withoutUserInfoClaim(String key) {
        this.userInfoClaims.remove(key);
        return this;
    }
    
    public OidcUserTestDataBuilder withAuthorities(Collection<? extends GrantedAuthority> authorities) {
        this.authorities = Set.copyOf(authorities);
        return this;
    }
    
    /**
     * Builds the OidcUser with the configured claims.
     */
    public OidcUser build() {
        OidcIdToken idToken = new OidcIdToken("test-token-value", 
            (Instant) idTokenClaims.get(IdTokenClaimNames.IAT),
            (Instant) idTokenClaims.get(IdTokenClaimNames.EXP),
            idTokenClaims);
            
        OidcUserInfo userInfo = userInfoClaims.isEmpty() ? null : new OidcUserInfo(userInfoClaims);
        
        return new DefaultOidcUser(authorities, idToken, userInfo, IdTokenClaimNames.SUB);
    }
    
    /**
     * Builds the OidcUser and also returns the claims for verification.
     */
    public OidcUserWithClaims buildWithClaims() {
        OidcUser user = build();
        return new OidcUserWithClaims(user, new HashMap<>(idTokenClaims), new HashMap<>(userInfoClaims));
    }
    
    /**
     * Helper class to return OidcUser with its claims for testing.
     */
    public static class OidcUserWithClaims {
        public final OidcUser user;
        public final Map<String, Object> idTokenClaims;
        public final Map<String, Object> userInfoClaims;
        
        public OidcUserWithClaims(OidcUser user, Map<String, Object> idTokenClaims, Map<String, Object> userInfoClaims) {
            this.user = user;
            this.idTokenClaims = idTokenClaims;
            this.userInfoClaims = userInfoClaims;
        }
    }
}