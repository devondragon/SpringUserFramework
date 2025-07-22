package com.digitalsanctuary.spring.user.integration;

import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.test.builders.UserTestDataBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Simple test to verify basic test infrastructure without full Spring context.
 */
@SpringBootTest(classes = SimpleInfrastructureTest.TestInfrastructureConfiguration.class)
@ActiveProfiles("test")
public class SimpleInfrastructureTest {

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
    
    /**
     * Minimal test configuration for testing builders.
     */
    @org.springframework.boot.test.context.TestConfiguration
    static class TestInfrastructureConfiguration {
        // Empty configuration just to have a valid Spring Boot test
    }
}