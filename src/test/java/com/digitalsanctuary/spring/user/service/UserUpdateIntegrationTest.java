package com.digitalsanctuary.spring.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.Arrays;
import java.util.HashSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import com.digitalsanctuary.spring.user.persistence.model.Role;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.repository.RoleRepository;
import com.digitalsanctuary.spring.user.persistence.repository.UserRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Integration test to verify that User entity updates work correctly with Hibernate.
 * This test specifically validates the fix for the UnsupportedOperationException
 * that was occurring when trying to update User entities with collection fields.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "user.registration.sendVerificationEmail=false",
    "user.security.failedLoginAttempts=3",
    "user.security.accountLockoutDuration=1"
})
class UserUpdateIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private LoginAttemptService loginAttemptService;

    @PersistenceContext
    private EntityManager entityManager;

    private Role userRole;
    private Role adminRole;

    @BeforeEach
    void setUp() {
        // Clean up
        userRepository.deleteAll();
        roleRepository.deleteAll();

        // Create roles
        userRole = new Role("ROLE_USER", "Basic user role");
        userRole = roleRepository.save(userRole);

        adminRole = new Role("ROLE_ADMIN", "Administrator role");
        adminRole = roleRepository.save(adminRole);
    }

    @Test
    @Transactional
    void testUserUpdateWithRolesCollection_NoUnsupportedOperationException() {
        // Create user with roles using List (backward compatible method)
        User user = new User();
        user.setEmail("test@example.com");
        user.setFirstName("Test");
        user.setLastName("User");
        user.setPassword("password");
        user.setEnabled(true);
        user.setRoles(Arrays.asList(userRole));
        
        user = userRepository.save(user);
        assertThat(user.getId()).isNotNull();
        assertThat(user.getRoles()).hasSize(1);

        // Clear the session to simulate real-world scenario
        entityManager.flush();
        entityManager.clear();

        // Load user again (simulating a new transaction)
        User loadedUser = userRepository.findByEmail("test@example.com");
        assertThat(loadedUser).isNotNull();

        // This should NOT throw UnsupportedOperationException
        assertDoesNotThrow(() -> {
            loadedUser.setFailedLoginAttempts(3);
            loadedUser.setLocked(true);
            userRepository.save(loadedUser);
        });

        // Verify the update worked
        entityManager.flush();
        entityManager.clear();
        
        User verifiedUser = userRepository.findByEmail("test@example.com");
        assertThat(verifiedUser.getFailedLoginAttempts()).isEqualTo(3);
        assertThat(verifiedUser.isLocked()).isTrue();
    }

    @Test
    @Transactional
    void testLoginAttemptService_CanUpdateUser() {
        // Create user with roles
        User user = new User();
        user.setEmail("login@example.com");
        user.setFirstName("Login");
        user.setLastName("Test");
        user.setPassword("password");
        user.setEnabled(true);
        user.setRoles(Arrays.asList(userRole));
        
        userRepository.save(user);

        // Clear session
        entityManager.flush();
        entityManager.clear();

        // Test login failed - this should work without UnsupportedOperationException
        assertDoesNotThrow(() -> {
            loginAttemptService.loginFailed("login@example.com");
        });

        User updatedUser = userRepository.findByEmail("login@example.com");
        assertThat(updatedUser.getFailedLoginAttempts()).isEqualTo(1);

        // Test multiple failures leading to account lock
        assertDoesNotThrow(() -> {
            loginAttemptService.loginFailed("login@example.com");
            loginAttemptService.loginFailed("login@example.com");
        });

        updatedUser = userRepository.findByEmail("login@example.com");
        assertThat(updatedUser.getFailedLoginAttempts()).isEqualTo(3);
        assertThat(updatedUser.isLocked()).isTrue();

        // Test login success - should reset attempts
        assertDoesNotThrow(() -> {
            loginAttemptService.loginSucceeded("login@example.com");
        });

        updatedUser = userRepository.findByEmail("login@example.com");
        assertThat(updatedUser.getFailedLoginAttempts()).isEqualTo(0);
        assertThat(updatedUser.isLocked()).isFalse();
    }

    @Test
    @Transactional
    void testUserRolesUpdate_UsingNewSetMethods() {
        // Create user with roles using the new Set methods
        User user = new User();
        user.setEmail("settest@example.com");
        user.setFirstName("Set");
        user.setLastName("Test");
        user.setPassword("password");
        user.setEnabled(true);
        user.setRolesAsSet(new HashSet<>(Arrays.asList(userRole)));
        
        user = userRepository.save(user);

        // Clear session
        entityManager.flush();
        entityManager.clear();

        // Load and update roles
        User loadedUser = userRepository.findByEmail("settest@example.com");
        
        // Add admin role using Set method
        assertDoesNotThrow(() -> {
            var currentRoles = loadedUser.getRolesAsSet();
            currentRoles.add(adminRole);
            loadedUser.setRolesAsSet(currentRoles);
            userRepository.save(loadedUser);
        });

        // Verify
        entityManager.flush();
        entityManager.clear();
        
        User verifiedUser = userRepository.findByEmail("settest@example.com");
        assertThat(verifiedUser.getRoles()).hasSize(2);
        assertThat(verifiedUser.getRoles()).extracting(Role::getName)
            .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    @Transactional
    void testBackwardCompatibility_ListMethodsStillWork() {
        // Create user using old List-based API
        User user = new User();
        user.setEmail("compat@example.com");
        user.setFirstName("Compat");
        user.setLastName("Test");
        user.setPassword("password");
        user.setEnabled(true);
        
        // Use List-based setter (backward compatible)
        user.setRoles(Arrays.asList(userRole, adminRole));
        userRepository.save(user);

        // Clear session
        entityManager.flush();
        entityManager.clear();

        // Load and verify List-based getter works
        User loadedUser = userRepository.findByEmail("compat@example.com");
        var rolesList = loadedUser.getRoles(); // Returns List<Role>
        
        assertThat(rolesList).isInstanceOf(java.util.List.class);
        assertThat(rolesList).hasSize(2);
        assertThat(rolesList).extracting(Role::getName)
            .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    @Transactional
    void testMultipleUpdatesInSameTransaction() {
        // Create user
        User user = new User();
        user.setEmail("multi@example.com");
        user.setFirstName("Multi");
        user.setLastName("Update");
        user.setPassword("password");
        user.setEnabled(true);
        user.setRoles(Arrays.asList(userRole));
        
        user = userRepository.save(user);
        final Long userId = user.getId();

        // Multiple updates in same transaction should work
        assertDoesNotThrow(() -> {
            User userToUpdate = userRepository.findById(userId).orElseThrow();
            userToUpdate.setFailedLoginAttempts(1);
            userRepository.save(userToUpdate);
            
            userToUpdate.setFailedLoginAttempts(2);
            userRepository.save(userToUpdate);
            
            userToUpdate.setLocked(true);
            userRepository.save(userToUpdate);
        });

        User finalUser = userRepository.findById(userId).orElseThrow();
        assertThat(finalUser.getFailedLoginAttempts()).isEqualTo(2);
        assertThat(finalUser.isLocked()).isTrue();
    }
}