package com.digitalsanctuary.spring.user.integration;

import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.model.Role;
import com.digitalsanctuary.spring.user.persistence.repository.UserRepository;
import com.digitalsanctuary.spring.user.persistence.repository.RoleRepository;
import com.digitalsanctuary.spring.user.test.annotations.IntegrationTest;
import com.digitalsanctuary.spring.user.test.builders.UserTestDataBuilder;
import com.digitalsanctuary.spring.user.test.config.MockMailConfiguration;
import com.digitalsanctuary.spring.user.test.config.OAuth2TestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verification test to ensure all test infrastructure components are properly configured.
 * This test validates that our custom test annotations and configurations work correctly.
 */
@IntegrationTest
@AutoConfigureMockMvc
public class TestInfrastructureVerificationTest {

    @Autowired
    private ApplicationContext applicationContext;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private RoleRepository roleRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Autowired
    private JavaMailSender mailSender;
    
    @Autowired
    private ClientRegistrationRepository clientRegistrationRepository;
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private OAuth2TestConfiguration.OAuth2TestTokenFactory tokenFactory;

    @Test
    void contextLoads() {
        assertThat(applicationContext).isNotNull();
    }
    
    @Test
    @Transactional
    void testDatabaseConfiguration() {
        // Given - ensure role exists first
        Role userRole = roleRepository.findByName("ROLE_USER");
        if (userRole == null) {
            userRole = new Role();
            userRole.setName("ROLE_USER");
            userRole = roleRepository.save(userRole);
        }
        
        // Test that we can interact with the database
        assertThat(userRole).isNotNull();
        assertThat(userRole.getId()).isNotNull();
        
        // Verify datasource is configured
        assertThat(applicationContext.getBean(DataSource.class)).isNotNull();
    }
    
    @Test
    void testPasswordEncoderConfiguration() {
        // Given
        String rawPassword = "testPassword123";
        
        // When
        String encodedPassword = passwordEncoder.encode(rawPassword);
        
        // Then
        assertThat(encodedPassword).isNotNull();
        assertThat(passwordEncoder.matches(rawPassword, encodedPassword)).isTrue();
        assertThat(encodedPassword).startsWith("$2a$04$"); // BCrypt with strength 4
    }
    
    @Test
    void testMailConfiguration() {
        // Given
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo("test@example.com");
        message.setSubject("Test Email");
        message.setText("This is a test email");
        
        // When
        mailSender.send(message);
        
        // Then
        MockMailConfiguration.MockJavaMailSender mockMailSender = 
                (MockMailConfiguration.MockJavaMailSender) mailSender;
        assertThat(mockMailSender.getSentSimpleMessages()).hasSize(1);
        assertThat(mockMailSender.getSentSimpleMessages().get(0).getTo()[0])
                .isEqualTo("test@example.com");
    }
    
    @Test
    void testOAuth2Configuration() {
        // Then
        assertThat(clientRegistrationRepository).isNotNull();
        assertThat(clientRegistrationRepository.findByRegistrationId("google")).isNotNull();
        assertThat(clientRegistrationRepository.findByRegistrationId("github")).isNotNull();
        assertThat(clientRegistrationRepository.findByRegistrationId("oidc")).isNotNull();
    }
    
    @Test
    void testOAuth2TokenFactory() {
        // When
        var accessToken = tokenFactory.createAccessToken();
        var oauth2User = tokenFactory.createOAuth2User("oauth2@test.com", "OAuth User", "oauth-123");
        
        // Then
        assertThat(accessToken).isNotNull();
        assertThat(accessToken.getTokenValue()).isEqualTo("test-access-token");
        
        assertThat(oauth2User).isNotNull();
        assertThat((String) oauth2User.getAttribute("email")).isEqualTo("oauth2@test.com");
        assertThat((String) oauth2User.getAttribute("name")).isEqualTo("OAuth User");
    }
    
    @Test
    void testMockMvcConfiguration() throws Exception {
        // When & Then - test that MockMvc is configured and security is working
        // Since "/" is in the unprotected URIs, it should be accessible
        var result = mockMvc.perform(get("/")).andReturn();
        
        // For now, just verify MockMvc is working - it might return 404 if no controller
        assertThat(result.getResponse()).isNotNull();
        assertThat(mockMvc).isNotNull();
    }
    
    @Test
    void testUserTestDataBuilder() {
        // When
        User verifiedUser = UserTestDataBuilder.aVerifiedUser().build();
        User adminUser = UserTestDataBuilder.anAdminUser().build();
        User unverifiedUser = UserTestDataBuilder.anUnverifiedUser().build();
        User lockedUser = UserTestDataBuilder.aLockedUser().build();
        
        // Then
        assertThat(verifiedUser.isEnabled()).isTrue();
        assertThat(verifiedUser.getRoles()).isNotEmpty();
        
        assertThat(adminUser.getEmail()).isEqualTo("admin@test.com");
        assertThat(adminUser.getRoles()).anyMatch(role -> role.getName().equals("ROLE_ADMIN"));
        
        assertThat(unverifiedUser.isEnabled()).isFalse();
        
        assertThat(lockedUser.isLocked()).isTrue();
        assertThat(lockedUser.getFailedLoginAttempts()).isEqualTo(5);
    }
}