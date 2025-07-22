package com.digitalsanctuary.spring.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;

import com.digitalsanctuary.spring.user.persistence.model.Privilege;
import com.digitalsanctuary.spring.user.persistence.model.Role;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.test.annotations.ServiceTest;
import com.digitalsanctuary.spring.user.test.builders.RoleTestDataBuilder;
import com.digitalsanctuary.spring.user.test.builders.UserTestDataBuilder;

/**
 * Unit tests for AuthorityService.
 * 
 * This test class verifies the authority generation logic including:
 * - Role to authority conversion
 * - Privilege deduplication
 * - Null and empty handling
 * - Edge cases and error conditions
 */
@ServiceTest
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthorityService Tests")
class AuthorityServiceTest {

    @InjectMocks
    private AuthorityService authorityService;

    private User testUser;
    private Role userRole;
    private Role adminRole;
    private Privilege readPrivilege;
    private Privilege writePrivilege;

    @BeforeEach
    void setUp() {
        // Create test privileges
        readPrivilege = new Privilege("READ_PRIVILEGE");
        readPrivilege.setId(1L);
        
        writePrivilege = new Privilege("WRITE_PRIVILEGE");
        writePrivilege.setId(2L);

        // Create test roles
        userRole = RoleTestDataBuilder.aRole()
                .withName("ROLE_USER")
                .withPrivilege(readPrivilege)
                .build();

        adminRole = RoleTestDataBuilder.aRole()
                .withName("ROLE_ADMIN")
                .withPrivilege(readPrivilege)
                .withPrivilege(writePrivilege)
                .build();

        // Create test user
        testUser = UserTestDataBuilder.aUser()
                .withEmail("test@example.com")
                .withRole(userRole)
                .build();
    }

    // Tests for getAuthoritiesFromUser

    @Test
    @DisplayName("Should handle null user")
    void getAuthoritiesFromUser_nullUser_throwsException() {
        assertThatThrownBy(() -> authorityService.getAuthoritiesFromUser(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Should return empty authorities for user with no roles")
    void getAuthoritiesFromUser_userWithNoRoles_returnsEmptyAuthorities() {
        // Given
        User userWithNoRoles = UserTestDataBuilder.aUser()
                .withEmail("noroles@example.com")
                .build();
        userWithNoRoles.setRoles(new ArrayList<>());

        // When
        Collection<? extends GrantedAuthority> authorities = authorityService.getAuthoritiesFromUser(userWithNoRoles);

        // Then
        assertThat(authorities).isEmpty();
    }

    @Test
    @DisplayName("Should convert user roles to authorities")
    void getAuthoritiesFromUser_userWithRoles_returnsCorrectAuthorities() {
        // Given
        testUser.setRoles(Arrays.asList(userRole, adminRole));

        // When
        Collection<? extends GrantedAuthority> authorities = authorityService.getAuthoritiesFromUser(testUser);

        // Then
        assertThat(authorities)
                .hasSize(2)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("READ_PRIVILEGE", "WRITE_PRIVILEGE");
    }

    // Tests for getAuthoritiesFromRoles

    @Test
    @DisplayName("Should handle null roles collection")
    void getAuthoritiesFromRoles_nullRoles_throwsException() {
        assertThatThrownBy(() -> authorityService.getAuthoritiesFromRoles(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Should return empty authorities for empty roles collection")
    void getAuthoritiesFromRoles_emptyRoles_returnsEmptyAuthorities() {
        // When
        Collection<? extends GrantedAuthority> authorities = authorityService.getAuthoritiesFromRoles(new ArrayList<>());

        // Then
        assertThat(authorities).isEmpty();
    }

    @Test
    @DisplayName("Should convert single role with single privilege")
    void getAuthoritiesFromRoles_singleRoleSinglePrivilege_returnsOneAuthority() {
        // Given
        List<Role> roles = Arrays.asList(userRole);

        // When
        Collection<? extends GrantedAuthority> authorities = authorityService.getAuthoritiesFromRoles(roles);

        // Then
        assertThat(authorities)
                .hasSize(1)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("READ_PRIVILEGE");
    }

    @Test
    @DisplayName("Should convert single role with multiple privileges")
    void getAuthoritiesFromRoles_singleRoleMultiplePrivileges_returnsMultipleAuthorities() {
        // Given
        List<Role> roles = Arrays.asList(adminRole);

        // When
        Collection<? extends GrantedAuthority> authorities = authorityService.getAuthoritiesFromRoles(roles);

        // Then
        assertThat(authorities)
                .hasSize(2)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("READ_PRIVILEGE", "WRITE_PRIVILEGE");
    }

    @Test
    @DisplayName("Should deduplicate authorities from multiple roles")
    void getAuthoritiesFromRoles_multipleRolesWithOverlappingPrivileges_deduplicatesAuthorities() {
        // Given
        List<Role> roles = Arrays.asList(userRole, adminRole);

        // When
        Collection<? extends GrantedAuthority> authorities = authorityService.getAuthoritiesFromRoles(roles);

        // Then - READ_PRIVILEGE appears in both roles but should only appear once
        assertThat(authorities)
                .hasSize(2)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("READ_PRIVILEGE", "WRITE_PRIVILEGE");
    }

    @Test
    @DisplayName("Should handle role with empty privileges")
    void getAuthoritiesFromRoles_roleWithEmptyPrivileges_returnsEmptyAuthorities() {
        // Given
        Role emptyRole = RoleTestDataBuilder.aRole()
                .withName("ROLE_EMPTY")
                .withNoPrivileges()
                .build();
        List<Role> roles = Arrays.asList(emptyRole);

        // When
        Collection<? extends GrantedAuthority> authorities = authorityService.getAuthoritiesFromRoles(roles);

        // Then
        assertThat(authorities).isEmpty();
    }

    @Test
    @DisplayName("Should handle role with null privileges set")
    void getAuthoritiesFromRoles_roleWithNullPrivileges_throwsException() {
        // Given
        Role roleWithNullPrivileges = new Role("ROLE_NULL");
        roleWithNullPrivileges.setPrivileges(null);
        List<Role> roles = Arrays.asList(roleWithNullPrivileges);

        // When & Then
        assertThatThrownBy(() -> authorityService.getAuthoritiesFromRoles(roles))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Should handle privilege with null name")
    void getAuthoritiesFromRoles_privilegeWithNullName_throwsException() {
        // Given
        Privilege nullNamePrivilege = new Privilege();
        nullNamePrivilege.setId(99L);
        nullNamePrivilege.setName(null);
        
        Role roleWithNullPrivilege = RoleTestDataBuilder.aRole()
                .withName("ROLE_TEST")
                .withPrivilege(nullNamePrivilege)
                .build();
        List<Role> roles = Arrays.asList(roleWithNullPrivilege);

        // When & Then - SimpleGrantedAuthority throws IllegalArgumentException for null
        assertThatThrownBy(() -> authorityService.getAuthoritiesFromRoles(roles))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("A granted authority textual representation is required");
    }

    @Test
    @DisplayName("Should handle privilege with empty name")
    void getAuthoritiesFromRoles_privilegeWithEmptyName_throwsException() {
        // Given
        Privilege emptyNamePrivilege = new Privilege("");
        emptyNamePrivilege.setId(100L);
        
        Role roleWithEmptyPrivilege = RoleTestDataBuilder.aRole()
                .withName("ROLE_TEST")
                .withPrivilege(emptyNamePrivilege)
                .build();
        List<Role> roles = Arrays.asList(roleWithEmptyPrivilege);

        // When & Then - SimpleGrantedAuthority throws IllegalArgumentException for empty string
        assertThatThrownBy(() -> authorityService.getAuthoritiesFromRoles(roles))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("A granted authority textual representation is required");
    }

    @Test
    @DisplayName("Should preserve case sensitivity in privilege names")
    void getAuthoritiesFromRoles_mixedCasePrivileges_preservesCase() {
        // Given
        Privilege lowerPrivilege = new Privilege("read_privilege");
        Privilege upperPrivilege = new Privilege("WRITE_PRIVILEGE");
        Privilege mixedPrivilege = new Privilege("Delete_Privilege");
        
        Role testRole = RoleTestDataBuilder.aRole()
                .withName("ROLE_TEST")
                .withPrivilege(lowerPrivilege)
                .withPrivilege(upperPrivilege)
                .withPrivilege(mixedPrivilege)
                .build();
        List<Role> roles = Arrays.asList(testRole);

        // When
        Collection<? extends GrantedAuthority> authorities = authorityService.getAuthoritiesFromRoles(roles);

        // Then
        assertThat(authorities)
                .hasSize(3)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("read_privilege", "WRITE_PRIVILEGE", "Delete_Privilege");
    }

    @Test
    @DisplayName("Should handle large number of privileges efficiently")
    void getAuthoritiesFromRoles_largeNumberOfPrivileges_handlesEfficiently() {
        // Given
        Role largeRole = RoleTestDataBuilder.aRole().withName("ROLE_LARGE").build();
        Set<Privilege> privileges = new HashSet<>();
        
        for (int i = 0; i < 1000; i++) {
            Privilege privilege = new Privilege("PRIVILEGE_" + i);
            privilege.setId((long) i);
            privileges.add(privilege);
        }
        largeRole.setPrivileges(privileges);
        
        List<Role> roles = Arrays.asList(largeRole);

        // When
        long startTime = System.currentTimeMillis();
        Collection<? extends GrantedAuthority> authorities = authorityService.getAuthoritiesFromRoles(roles);
        long endTime = System.currentTimeMillis();

        // Then
        assertThat(authorities).hasSize(1000);
        assertThat(endTime - startTime).isLessThan(100); // Should complete in less than 100ms
    }

    @Test
    @DisplayName("Should handle complex deduplication scenario")
    void getAuthoritiesFromRoles_complexOverlap_correctlyDeduplicates() {
        // Given
        Privilege commonPrivilege = new Privilege("COMMON_PRIVILEGE");
        
        Role role1 = RoleTestDataBuilder.aRole()
                .withName("ROLE_1")
                .withPrivilege(commonPrivilege)
                .withPrivilege("UNIQUE_1")
                .build();
                
        Role role2 = RoleTestDataBuilder.aRole()
                .withName("ROLE_2")
                .withPrivilege(commonPrivilege)
                .withPrivilege("UNIQUE_2")
                .build();
                
        Role role3 = RoleTestDataBuilder.aRole()
                .withName("ROLE_3")
                .withPrivilege(commonPrivilege)
                .withPrivilege("UNIQUE_1") // Duplicate from role1
                .withPrivilege("UNIQUE_3")
                .build();
                
        List<Role> roles = Arrays.asList(role1, role2, role3);

        // When
        Collection<? extends GrantedAuthority> authorities = authorityService.getAuthoritiesFromRoles(roles);

        // Then
        assertThat(authorities)
                .hasSize(4) // COMMON_PRIVILEGE, UNIQUE_1, UNIQUE_2, UNIQUE_3
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("COMMON_PRIVILEGE", "UNIQUE_1", "UNIQUE_2", "UNIQUE_3");
    }
}