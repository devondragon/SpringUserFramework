package com.digitalsanctuary.spring.user.unit;

import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.test.builders.UserTestDataBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for UserTestDataBuilder to verify it works correctly.
 */
public class UserTestDataBuilderTest {

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
        assertThat(verifiedUser.getEmail()).contains("@test.com");
        
        assertThat(adminUser.getEmail()).isEqualTo("admin@test.com");
        assertThat(adminUser.getRoles()).anyMatch(role -> role.getName().equals("ROLE_ADMIN"));
        
        assertThat(unverifiedUser.isEnabled()).isFalse();
        assertThat(unverifiedUser.getEmail()).isEqualTo("unverified@test.com");
        
        assertThat(lockedUser.isLocked()).isTrue();
        assertThat(lockedUser.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(lockedUser.getEmail()).isEqualTo("locked@test.com");
    }
    
    @Test
    void testUserBuilderWithCustomization() {
        // When
        User customUser = UserTestDataBuilder.aUser()
                .withEmail("custom@example.com")
                .withFirstName("John")
                .withLastName("Doe")
                .withPassword("customPassword")
                .enabled()
                .withRole("ROLE_CUSTOM")
                .build();
        
        // Then
        assertThat(customUser.getEmail()).isEqualTo("custom@example.com");
        assertThat(customUser.getFirstName()).isEqualTo("John");
        assertThat(customUser.getLastName()).isEqualTo("Doe");
        assertThat(customUser.isEnabled()).isTrue();
        assertThat(customUser.getRoles()).hasSize(1);
        assertThat(customUser.getRoles().get(0).getName()).isEqualTo("ROLE_CUSTOM");
    }
    
    @Test
    void testUserBuilderDateMethods() {
        // When
        User oldUser = UserTestDataBuilder.aUser()
                .registeredDaysAgo(30)
                .lastActiveDaysAgo(7)
                .build();
        
        // Then
        assertThat(oldUser.getRegistrationDate()).isNotNull();
        assertThat(oldUser.getLastActivityDate()).isNotNull();
        assertThat(oldUser.getRegistrationDate()).isBefore(oldUser.getLastActivityDate());
    }
}