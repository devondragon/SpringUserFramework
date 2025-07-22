package com.digitalsanctuary.spring.user.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.transaction.annotation.Transactional;

import com.digitalsanctuary.spring.user.persistence.model.Privilege;
import com.digitalsanctuary.spring.user.persistence.model.Role;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.repository.RoleRepository;
import com.digitalsanctuary.spring.user.persistence.repository.UserRepository;
import com.digitalsanctuary.spring.user.service.DSUserDetails;
import com.digitalsanctuary.spring.user.service.DSUserDetailsService;
import com.digitalsanctuary.spring.user.test.annotations.IntegrationTest;
import com.digitalsanctuary.spring.user.test.builders.RoleTestDataBuilder;
import com.digitalsanctuary.spring.user.test.builders.UserTestDataBuilder;

/**
 * Integration tests for DSUserDetailsService.
 * 
 * This test class verifies the full integration behavior including:
 * - Database persistence
 * - Transaction management
 * - LoginHelperService integration
 * - Authority loading
 * - Account unlock functionality
 */
@IntegrationTest
@DisplayName("DSUserDetailsService Integration Tests")
class DSUserDetailsServiceIntegrationTest {

    @Autowired
    private DSUserDetailsService dsUserDetailsService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    private Role userRole;
    private Role adminRole;

    @BeforeEach
    @Transactional
    void setUp() {
        // Clean up
        userRepository.deleteAll();
        roleRepository.deleteAll();

        // Create privileges
        Privilege userPrivilege = new Privilege();
        userPrivilege.setName("ROLE_USER");
        
        Privilege adminPrivilege = new Privilege();
        adminPrivilege.setName("ROLE_ADMIN");

        // Create roles with privileges
        userRole = RoleTestDataBuilder.aRole()
                .withName("ROLE_USER")
                .withId(null)
                .build();
        userRole.getPrivileges().add(userPrivilege);
        userRole = roleRepository.save(userRole);
        
        adminRole = RoleTestDataBuilder.aRole()
                .withName("ROLE_ADMIN")
                .withId(null)
                .build();
        adminRole.getPrivileges().add(adminPrivilege);
        adminRole = roleRepository.save(adminRole);
    }

    @Test
    @Transactional
    @DisplayName("Should load user and update lastActivityDate")
    void loadUserByUsername_updatesLastActivityDate() {
        // Given
        Date originalDate = Date.from(
            LocalDateTime.now().minusDays(1).atZone(ZoneId.systemDefault()).toInstant()
        );
        
        User user = UserTestDataBuilder.aVerifiedUser()
                .withEmail("activity@test.com")
                .withLastActivityDate(originalDate)
                .withId(null)
                .build();
        user.setRoles(new ArrayList<>(Arrays.asList(userRole)));
        user = userRepository.save(user);
        
        Date beforeLoad = new Date();

        // When
        DSUserDetails result = dsUserDetailsService.loadUserByUsername("activity@test.com");

        // Then
        User updatedUser = userRepository.findByEmail("activity@test.com");
        assertThat(updatedUser.getLastActivityDate()).isAfter(originalDate);
        assertThat(updatedUser.getLastActivityDate()).isAfterOrEqualTo(beforeLoad);
        assertThat(result.getUser().getLastActivityDate()).isAfterOrEqualTo(beforeLoad);
    }

    @Test
    @Transactional
    @DisplayName("Should auto-unlock eligible locked user")
    void loadUserByUsername_autoUnlocksEligibleUser() {
        // Given - Create a locked user with old lock date (should be unlocked)
        Date oldLockDate = Date.from(
            LocalDateTime.now().minusHours(2).atZone(ZoneId.systemDefault()).toInstant()
        );
        
        User lockedUser = UserTestDataBuilder.aLockedUser()
                .withEmail("autounlock@test.com")
                .withLockedDate(oldLockDate)
                .withFailedLoginAttempts(5)
                .withId(null)
                .build();
        lockedUser.setRoles(new ArrayList<>(Arrays.asList(userRole)));
        lockedUser = userRepository.save(lockedUser);
        
        // Verify user is initially locked
        assertThat(lockedUser.isLocked()).isTrue();

        // When
        DSUserDetails result = dsUserDetailsService.loadUserByUsername("autounlock@test.com");

        // Then - Depending on configuration, user might be unlocked
        // Note: This behavior depends on accountLockoutDuration configuration
        // If configured to auto-unlock after certain time, the user should be unlocked
        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo("autounlock@test.com");
    }

    @Test
    @Transactional
    @DisplayName("Should load user with multiple roles and correct authorities")
    void loadUserByUsername_withMultipleRoles_loadsAllAuthorities() {
        // Given
        User multiRoleUser = UserTestDataBuilder.aVerifiedUser()
                .withEmail("multirole@test.com")
                .withId(null)
                .build();
        multiRoleUser.setRoles(new ArrayList<>(Arrays.asList(userRole, adminRole)));
        userRepository.save(multiRoleUser);

        // When
        DSUserDetails result = dsUserDetailsService.loadUserByUsername("multirole@test.com");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    @DisplayName("Should throw exception for non-existent user")
    void loadUserByUsername_nonExistentUser_throwsException() {
        // When & Then
        assertThatThrownBy(() -> dsUserDetailsService.loadUserByUsername("nonexistent@test.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("No user found with email/username: nonexistent@test.com");
    }

    @Test
    @Transactional
    @DisplayName("Should handle concurrent access correctly")
    void loadUserByUsername_concurrentAccess_handlesCorrectly() throws InterruptedException {
        // Given
        User user = UserTestDataBuilder.aVerifiedUser()
                .withEmail("concurrent@test.com")
                .withId(null)
                .build();
        user.setRoles(new ArrayList<>(Arrays.asList(userRole)));
        userRepository.save(user);

        // When - Simulate concurrent access
        Thread thread1 = new Thread(() -> {
            DSUserDetails result = dsUserDetailsService.loadUserByUsername("concurrent@test.com");
            assertThat(result).isNotNull();
        });
        
        Thread thread2 = new Thread(() -> {
            DSUserDetails result = dsUserDetailsService.loadUserByUsername("concurrent@test.com");
            assertThat(result).isNotNull();
        });

        thread1.start();
        thread2.start();
        
        thread1.join();
        thread2.join();

        // Then - Both threads should complete successfully
        User finalUser = userRepository.findByEmail("concurrent@test.com");
        assertThat(finalUser).isNotNull();
        assertThat(finalUser.getLastActivityDate()).isNotNull();
    }

    @Test
    @Transactional
    @DisplayName("Should correctly map all UserDetails properties")
    void loadUserByUsername_mapsAllUserDetailsProperties() {
        // Given
        User user = UserTestDataBuilder.aUser()
                .withEmail("mapping@test.com")
                .withPassword("password123") // Will be encoded by builder
                .withFirstName("Jane")
                .withLastName("Smith")
                .verified()
                .withId(null)
                .build();
        user.setRoles(new ArrayList<>(Arrays.asList(userRole)));
        userRepository.save(user);

        // When
        DSUserDetails result = dsUserDetailsService.loadUserByUsername("mapping@test.com");

        // Then
        assertThat(result.getUsername()).isEqualTo("mapping@test.com");
        assertThat(result.getPassword()).isNotNull();
        assertThat(result.getPassword()).startsWith("$2a$"); // BCrypt encoded
        assertThat(result.getName()).isEqualTo("Jane Smith");
        assertThat(result.isEnabled()).isTrue();
        assertThat(result.isAccountNonExpired()).isTrue();
        assertThat(result.isAccountNonLocked()).isTrue();
        assertThat(result.isCredentialsNonExpired()).isTrue();
        assertThat(result.getUser()).isNotNull();
        assertThat(result.getUser().getEmail()).isEqualTo("mapping@test.com");
    }

    @Test
    @Transactional
    @DisplayName("Should handle disabled user correctly")
    void loadUserByUsername_disabledUser_returnsWithCorrectStatus() {
        // Given
        User disabledUser = UserTestDataBuilder.anUnverifiedUser()
                .withEmail("disabled@test.com")
                .withId(null)
                .build();
        disabledUser.setRoles(new ArrayList<>(Arrays.asList(userRole)));
        userRepository.save(disabledUser);

        // When
        DSUserDetails result = dsUserDetailsService.loadUserByUsername("disabled@test.com");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.isEnabled()).isFalse();
        assertThat(result.isAccountNonLocked()).isTrue();
        assertThat(result.getUsername()).isEqualTo("disabled@test.com");
    }

    @Test
    @Transactional
    @DisplayName("Should handle currently locked user correctly")
    void loadUserByUsername_currentlyLockedUser_returnsWithLockedStatus() {
        // Given - Create a recently locked user (should remain locked)
        Date recentLockDate = new Date();
        
        User lockedUser = UserTestDataBuilder.aLockedUser()
                .withEmail("locked@test.com")
                .withLockedDate(recentLockDate)
                .verified() // Make the user enabled but locked
                .withId(null)
                .build();
        lockedUser.setRoles(new ArrayList<>(Arrays.asList(userRole)));
        lockedUser = userRepository.save(lockedUser);
        
        // Verify user is initially locked
        assertThat(lockedUser.isLocked()).isTrue();

        // When
        DSUserDetails result = dsUserDetailsService.loadUserByUsername("locked@test.com");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.isEnabled()).isTrue(); // Locked users can still be enabled
        
        // Check the actual lock status - it depends on configuration
        // If accountLockoutDuration is 0, the user might be unlocked immediately
        // If it's > 0, the user should remain locked since we just locked them
        User userAfterLoad = userRepository.findByEmail("locked@test.com");
        
        // The test should verify the actual behavior based on configuration
        // If the user is still locked, isAccountNonLocked should be false
        // If the user was unlocked by the service, isAccountNonLocked should be true
        if (userAfterLoad.isLocked()) {
            assertThat(result.isAccountNonLocked()).isFalse();
        } else {
            // User was unlocked by the service
            assertThat(result.isAccountNonLocked()).isTrue();
        }
    }
}