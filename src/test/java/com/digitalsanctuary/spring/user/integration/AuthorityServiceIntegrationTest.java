package com.digitalsanctuary.spring.user.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.transaction.annotation.Transactional;

import com.digitalsanctuary.spring.user.persistence.model.Privilege;
import com.digitalsanctuary.spring.user.persistence.model.Role;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.repository.PrivilegeRepository;
import com.digitalsanctuary.spring.user.persistence.repository.RoleRepository;
import com.digitalsanctuary.spring.user.persistence.repository.UserRepository;
import com.digitalsanctuary.spring.user.service.AuthorityService;
import com.digitalsanctuary.spring.user.test.annotations.IntegrationTest;
import com.digitalsanctuary.spring.user.test.builders.UserTestDataBuilder;

/**
 * Integration tests for AuthorityService.
 * 
 * This test class verifies the full integration behavior including:
 * - Database persistence and lazy loading
 * - Transaction management
 * - Real entity relationships
 * - Configuration-based role hierarchies
 */
@IntegrationTest
@DisplayName("AuthorityService Integration Tests")
class AuthorityServiceIntegrationTest {

    @Autowired
    private AuthorityService authorityService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;
    
    @Autowired
    private PrivilegeRepository privilegeRepository;

    private User testUser;
    private Role userRole;
    private Role managerRole;
    private Role adminRole;

    @BeforeEach
    @Transactional
    void setUp() {
        // Clean up
        userRepository.deleteAll();
        roleRepository.deleteAll();
        privilegeRepository.deleteAll();

        // Create privileges as defined in config
        Privilege loginPrivilege = createAndSavePrivilege("LOGIN_PRIVILEGE", "Allows user login");
        Privilege updateOwnPrivilege = createAndSavePrivilege("UPDATE_OWN_USER_PRIVILEGE", "Update own user profile");
        Privilege resetOwnPrivilege = createAndSavePrivilege("RESET_OWN_PASSWORD_PRIVILEGE", "Reset own password");
        
        Privilege addToTeamPrivilege = createAndSavePrivilege("ADD_USER_TO_TEAM_PRIVILEGE", "Add users to team");
        Privilege removeFromTeamPrivilege = createAndSavePrivilege("REMOVE_USER_FROM_TEAM_PRIVILEGE", "Remove users from team");
        Privilege resetTeamPrivilege = createAndSavePrivilege("RESET_TEAM_PASSWORD_PRIVILEGE", "Reset team passwords");
        
        Privilege adminPrivilege = createAndSavePrivilege("ADMIN_PRIVILEGE", "Full admin access");
        Privilege invitePrivilege = createAndSavePrivilege("INVITE_USER_PRIVILEGE", "Invite new users");
        Privilege readUserPrivilege = createAndSavePrivilege("READ_USER_PRIVILEGE", "Read user data");
        Privilege assignManagerPrivilege = createAndSavePrivilege("ASSIGN_MANAGER_PRIVILEGE", "Assign managers");
        Privilege resetAnyPrivilege = createAndSavePrivilege("RESET_ANY_USER_PASSWORD_PRIVILEGE", "Reset any password");

        // Create roles with privileges matching config
        userRole = createRole("ROLE_USER", "Standard user role",
                loginPrivilege, updateOwnPrivilege, resetOwnPrivilege);
        
        managerRole = createRole("ROLE_MANAGER", "Manager role",
                addToTeamPrivilege, removeFromTeamPrivilege, resetTeamPrivilege);
        
        adminRole = createRole("ROLE_ADMIN", "Administrator role",
                adminPrivilege, invitePrivilege, readUserPrivilege, assignManagerPrivilege, resetAnyPrivilege);

        // Create test user
        testUser = UserTestDataBuilder.aVerifiedUser()
                .withEmail("test@example.com")
                .withId(null)
                .build();
        testUser.setRoles(Arrays.asList(userRole));
        testUser = userRepository.save(testUser);
    }

    private Privilege createAndSavePrivilege(String name, String description) {
        Privilege privilege = new Privilege(name, description);
        return privilegeRepository.save(privilege);
    }

    private Role createRole(String name, String description, Privilege... privileges) {
        Role role = new Role(name, description);
        role.getPrivileges().addAll(Arrays.asList(privileges));
        return roleRepository.save(role);
    }

    @Test
    @Transactional
    @DisplayName("Should load authorities from persisted user with lazy loading")
    void getAuthoritiesFromUser_persistedUser_loadsAuthoritiesWithLazyLoading() {
        // Given - Fetch user in new transaction to test lazy loading
        User fetchedUser = userRepository.findByEmail("test@example.com");

        // When
        Collection<? extends GrantedAuthority> authorities = authorityService.getAuthoritiesFromUser(fetchedUser);

        // Then
        assertThat(authorities)
                .hasSize(3)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder(
                        "LOGIN_PRIVILEGE",
                        "UPDATE_OWN_USER_PRIVILEGE",
                        "RESET_OWN_PASSWORD_PRIVILEGE"
                );
    }

    @Test
    @Transactional
    @DisplayName("Should handle user with multiple roles from database")
    void getAuthoritiesFromUser_multipleRoles_combinesAllPrivileges() {
        // Given
        User multiRoleUser = UserTestDataBuilder.aVerifiedUser()
                .withEmail("multirole@example.com")
                .withId(null)
                .build();
        multiRoleUser.setRoles(Arrays.asList(userRole, managerRole));
        multiRoleUser = userRepository.save(multiRoleUser);

        // When
        Collection<? extends GrantedAuthority> authorities = authorityService.getAuthoritiesFromUser(multiRoleUser);

        // Then
        assertThat(authorities)
                .hasSize(6) // 3 from user role + 3 from manager role
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder(
                        "LOGIN_PRIVILEGE",
                        "UPDATE_OWN_USER_PRIVILEGE",
                        "RESET_OWN_PASSWORD_PRIVILEGE",
                        "ADD_USER_TO_TEAM_PRIVILEGE",
                        "REMOVE_USER_FROM_TEAM_PRIVILEGE",
                        "RESET_TEAM_PASSWORD_PRIVILEGE"
                );
    }

    @Test
    @Transactional
    @DisplayName("Should handle admin user with all configured privileges")
    void getAuthoritiesFromUser_adminUser_hasAllAdminPrivileges() {
        // Given
        User adminUser = UserTestDataBuilder.anAdminUser()
                .withEmail("admin@example.com")
                .withId(null)
                .build();
        adminUser.setRoles(Arrays.asList(adminRole));
        adminUser = userRepository.save(adminUser);

        // When
        Collection<? extends GrantedAuthority> authorities = authorityService.getAuthoritiesFromUser(adminUser);

        // Then
        assertThat(authorities)
                .hasSize(5)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder(
                        "ADMIN_PRIVILEGE",
                        "INVITE_USER_PRIVILEGE",
                        "READ_USER_PRIVILEGE",
                        "ASSIGN_MANAGER_PRIVILEGE",
                        "RESET_ANY_USER_PASSWORD_PRIVILEGE"
                );
    }

    @Test
    @Transactional
    @DisplayName("Should deduplicate when roles share privileges")
    void getAuthoritiesFromRoles_sharedPrivileges_deduplicatesCorrectly() {
        // Given - Create roles with overlapping privileges
        Privilege sharedPrivilege = createAndSavePrivilege("SHARED_PRIVILEGE", "Shared between roles");
        
        Role role1 = createRole("ROLE_TEST1", "Test role 1", sharedPrivilege);
        role1.getPrivileges().addAll(userRole.getPrivileges()); // Add user privileges
        role1 = roleRepository.save(role1);
        
        Role role2 = createRole("ROLE_TEST2", "Test role 2", sharedPrivilege);
        role2.getPrivileges().addAll(userRole.getPrivileges()); // Add same user privileges
        role2 = roleRepository.save(role2);

        // When
        Collection<? extends GrantedAuthority> authorities = authorityService.getAuthoritiesFromRoles(Arrays.asList(role1, role2));

        // Then - Should only have 4 unique privileges (3 from user + 1 shared)
        assertThat(authorities)
                .hasSize(4)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder(
                        "LOGIN_PRIVILEGE",
                        "UPDATE_OWN_USER_PRIVILEGE",
                        "RESET_OWN_PASSWORD_PRIVILEGE",
                        "SHARED_PRIVILEGE"
                );
    }

    @Test
    @Transactional
    @DisplayName("Should handle cascading operations correctly")
    void getAuthoritiesFromUser_cascadingOperations_maintainsIntegrity() {
        // Given - Create user with role that will be modified
        User user = UserTestDataBuilder.aVerifiedUser()
                .withEmail("cascade@example.com")
                .withId(null)
                .build();
        user.setRoles(Arrays.asList(userRole));
        user = userRepository.save(user);

        // Add new privilege to existing role
        Privilege newPrivilege = createAndSavePrivilege("NEW_PRIVILEGE", "Newly added privilege");
        userRole.getPrivileges().add(newPrivilege);
        roleRepository.save(userRole);

        // When - Fetch user again and get authorities
        User updatedUser = userRepository.findByEmail("cascade@example.com");
        Collection<? extends GrantedAuthority> authorities = authorityService.getAuthoritiesFromUser(updatedUser);

        // Then - Should include the new privilege
        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .contains("NEW_PRIVILEGE");
    }

    @Test
    @DisplayName("Should handle roles loaded separately from database")
    void getAuthoritiesFromRoles_separatelyLoadedRoles_worksCorrectly() {
        // Given - Load roles separately (simulating different transaction contexts)
        Role loadedUserRole = roleRepository.findById(userRole.getId()).orElseThrow();
        Role loadedManagerRole = roleRepository.findById(managerRole.getId()).orElseThrow();
        Role loadedAdminRole = roleRepository.findById(adminRole.getId()).orElseThrow();

        // When
        Collection<? extends GrantedAuthority> authorities = authorityService.getAuthoritiesFromRoles(
                Arrays.asList(loadedUserRole, loadedManagerRole, loadedAdminRole)
        );

        // Then - Should have all unique privileges from all three roles
        assertThat(authorities)
                .hasSize(11) // 3 + 3 + 5 unique privileges
                .extracting(GrantedAuthority::getAuthority)
                .contains(
                        // User privileges
                        "LOGIN_PRIVILEGE",
                        "UPDATE_OWN_USER_PRIVILEGE",
                        "RESET_OWN_PASSWORD_PRIVILEGE",
                        // Manager privileges
                        "ADD_USER_TO_TEAM_PRIVILEGE",
                        "REMOVE_USER_FROM_TEAM_PRIVILEGE",
                        "RESET_TEAM_PASSWORD_PRIVILEGE",
                        // Admin privileges
                        "ADMIN_PRIVILEGE",
                        "INVITE_USER_PRIVILEGE",
                        "READ_USER_PRIVILEGE",
                        "ASSIGN_MANAGER_PRIVILEGE",
                        "RESET_ANY_USER_PASSWORD_PRIVILEGE"
                );
    }

    @Test
    @Transactional
    @DisplayName("Should maintain transactional consistency")
    void getAuthoritiesFromUser_transactionalConsistency_maintainsIntegrity() {
        // Given - Create a user and verify initial state
        User user = UserTestDataBuilder.aVerifiedUser()
                .withEmail("transactional@example.com")
                .withId(null)
                .build();
        user.setRoles(Arrays.asList(userRole));
        user = userRepository.save(user);
        
        // Get initial authorities
        Collection<? extends GrantedAuthority> initialAuthorities = authorityService.getAuthoritiesFromUser(user);
        int initialSize = initialAuthorities.size();

        // When - Modify role in same transaction
        Privilege tempPrivilege = createAndSavePrivilege("TEMP_PRIVILEGE", "Temporary privilege");
        userRole.getPrivileges().add(tempPrivilege);
        roleRepository.save(userRole);
        
        // Get authorities again
        Collection<? extends GrantedAuthority> updatedAuthorities = authorityService.getAuthoritiesFromUser(user);

        // Then
        assertThat(updatedAuthorities).hasSize(initialSize + 1);
        assertThat(updatedAuthorities)
                .extracting(GrantedAuthority::getAuthority)
                .contains("TEMP_PRIVILEGE");
    }
}