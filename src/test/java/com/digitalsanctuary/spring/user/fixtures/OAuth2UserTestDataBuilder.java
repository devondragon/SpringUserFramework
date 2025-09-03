package com.digitalsanctuary.spring.user.fixtures;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * Test data builder for creating OAuth2User instances with realistic provider-specific attributes.
 * Supports Google and Facebook OAuth2 providers with accurate attribute mappings.
 */
public class OAuth2UserTestDataBuilder {
    
    private Map<String, Object> attributes = new HashMap<>();
    private Set<GrantedAuthority> authorities = Set.of(new SimpleGrantedAuthority("ROLE_USER"));
    private String nameAttributeKey = "sub";
    
    private OAuth2UserTestDataBuilder() {
        // Private constructor to enforce builder pattern
    }
    
    /**
     * Creates a builder for Google OAuth2 user with standard Google attributes.
     */
    public static OAuth2UserTestDataBuilder google() {
        OAuth2UserTestDataBuilder builder = new OAuth2UserTestDataBuilder();
        builder.nameAttributeKey = "sub";
        // Set default Google attributes
        builder.attributes.put("sub", "123456789");
        builder.attributes.put("email", "test.user@gmail.com");
        builder.attributes.put("email_verified", true);
        builder.attributes.put("given_name", "Test");
        builder.attributes.put("family_name", "User");
        builder.attributes.put("name", "Test User");
        builder.attributes.put("picture", "https://lh3.googleusercontent.com/a/test-picture");
        builder.attributes.put("locale", "en");
        return builder;
    }
    
    /**
     * Creates a builder for Facebook OAuth2 user with standard Facebook attributes.
     */
    public static OAuth2UserTestDataBuilder facebook() {
        OAuth2UserTestDataBuilder builder = new OAuth2UserTestDataBuilder();
        builder.nameAttributeKey = "id";
        // Set default Facebook attributes
        builder.attributes.put("id", "987654321");
        builder.attributes.put("email", "test.user@facebook.com");
        builder.attributes.put("name", "Test User");
        builder.attributes.put("first_name", "Test");
        builder.attributes.put("last_name", "User");
        return builder;
    }
    
    /**
     * Creates a builder for an unsupported OAuth2 provider.
     */
    public static OAuth2UserTestDataBuilder unsupported() {
        OAuth2UserTestDataBuilder builder = new OAuth2UserTestDataBuilder();
        builder.nameAttributeKey = "sub";
        builder.attributes.put("sub", "unknown-provider");
        builder.attributes.put("email", "test@unknown.com");
        return builder;
    }
    
    /**
     * Creates a builder with minimal attributes (missing required fields).
     */
    public static OAuth2UserTestDataBuilder minimal() {
        OAuth2UserTestDataBuilder builder = new OAuth2UserTestDataBuilder();
        builder.nameAttributeKey = "sub";
        builder.attributes.put("sub", "minimal-user");
        return builder;
    }
    
    public OAuth2UserTestDataBuilder withEmail(String email) {
        this.attributes.put("email", email);
        return this;
    }
    
    public OAuth2UserTestDataBuilder withFirstName(String firstName) {
        if (attributes.containsKey("given_name")) {
            // Google format
            this.attributes.put("given_name", firstName);
        } else {
            // Facebook format
            this.attributes.put("first_name", firstName);
        }
        return this;
    }
    
    public OAuth2UserTestDataBuilder withLastName(String lastName) {
        if (attributes.containsKey("family_name")) {
            // Google format
            this.attributes.put("family_name", lastName);
        } else {
            // Facebook format
            this.attributes.put("last_name", lastName);
        }
        return this;
    }
    
    public OAuth2UserTestDataBuilder withFullName(String fullName) {
        this.attributes.put("name", fullName);
        return this;
    }
    
    public OAuth2UserTestDataBuilder withAttribute(String key, Object value) {
        this.attributes.put(key, value);
        return this;
    }
    
    public OAuth2UserTestDataBuilder withoutAttribute(String key) {
        this.attributes.remove(key);
        return this;
    }
    
    public OAuth2UserTestDataBuilder withAuthorities(Collection<? extends GrantedAuthority> authorities) {
        this.authorities = Set.copyOf(authorities);
        return this;
    }
    
    /**
     * Builds the OAuth2User with the configured attributes.
     */
    public OAuth2User build() {
        return new DefaultOAuth2User(authorities, attributes, nameAttributeKey);
    }
    
    /**
     * Builds the OAuth2User and also returns the attributes map for verification.
     */
    public OAuth2UserWithAttributes buildWithAttributes() {
        OAuth2User user = build();
        return new OAuth2UserWithAttributes(user, new HashMap<>(attributes));
    }
    
    /**
     * Helper class to return both OAuth2User and its attributes for testing.
     */
    public static class OAuth2UserWithAttributes {
        public final OAuth2User user;
        public final Map<String, Object> attributes;
        
        public OAuth2UserWithAttributes(OAuth2User user, Map<String, Object> attributes) {
            this.user = user;
            this.attributes = attributes;
        }
    }
}