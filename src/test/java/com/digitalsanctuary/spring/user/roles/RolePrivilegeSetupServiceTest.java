package com.digitalsanctuary.spring.user.roles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.event.ContextRefreshedEvent;

import com.digitalsanctuary.spring.user.persistence.model.Privilege;
import com.digitalsanctuary.spring.user.persistence.model.Role;
import com.digitalsanctuary.spring.user.persistence.repository.PrivilegeRepository;
import com.digitalsanctuary.spring.user.persistence.repository.RoleRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("RolePrivilegeSetupService Tests")
class RolePrivilegeSetupServiceTest {

    @Mock
    private RolesAndPrivilegesConfig rolesAndPrivilegesConfig;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PrivilegeRepository privilegeRepository;

    @Mock
    private ContextRefreshedEvent contextRefreshedEvent;

    private RolePrivilegeSetupService rolePrivilegeSetupService;

    @BeforeEach
    void setUp() {
        rolePrivilegeSetupService = new RolePrivilegeSetupService(
                rolesAndPrivilegesConfig, 
                roleRepository, 
                privilegeRepository
        );
    }

    @Nested
    @DisplayName("Application Event Handling Tests")
    class ApplicationEventHandlingTests {

        @Test
        @DisplayName("Should set up roles and privileges on first context refresh")
        void shouldSetupRolesAndPrivilegesOnFirstContextRefresh() {
            // Given
            Map<String, List<String>> rolesAndPrivileges = new HashMap<>();
            rolesAndPrivileges.put("ROLE_ADMIN", Arrays.asList("READ_PRIVILEGE", "WRITE_PRIVILEGE", "DELETE_PRIVILEGE"));
            rolesAndPrivileges.put("ROLE_USER", Arrays.asList("READ_PRIVILEGE"));
            
            when(rolesAndPrivilegesConfig.getRolesAndPrivileges()).thenReturn(rolesAndPrivileges);
            when(privilegeRepository.findByName(anyString())).thenReturn(null);
            when(privilegeRepository.save(any(Privilege.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(roleRepository.findByName(anyString())).thenReturn(null);
            when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            rolePrivilegeSetupService.onApplicationEvent(contextRefreshedEvent);

            // Then
            // READ_PRIVILEGE appears twice (for ROLE_ADMIN and ROLE_USER), so 4 total lookups
            verify(privilegeRepository, times(4)).findByName(anyString());
            // All 4 privileges will be saved (READ is saved twice because findByName returns null each time)
            verify(privilegeRepository, times(4)).save(any(Privilege.class));
            verify(roleRepository, times(2)).findByName(anyString());
            verify(roleRepository, times(2)).save(any(Role.class));
            assertThat(rolePrivilegeSetupService.isAlreadySetup()).isTrue();
        }

        @Test
        @DisplayName("Should not set up roles and privileges on subsequent context refreshes")
        void shouldNotSetupOnSubsequentContextRefreshes() {
            // Given
            rolePrivilegeSetupService.setAlreadySetup(true);

            // When
            rolePrivilegeSetupService.onApplicationEvent(contextRefreshedEvent);

            // Then
            verify(rolesAndPrivilegesConfig, never()).getRolesAndPrivileges();
            verify(privilegeRepository, never()).findByName(anyString());
            verify(roleRepository, never()).findByName(anyString());
        }

        @Test
        @DisplayName("Should handle empty configuration gracefully")
        void shouldHandleEmptyConfigurationGracefully() {
            // Given
            when(rolesAndPrivilegesConfig.getRolesAndPrivileges()).thenReturn(new HashMap<>());

            // When
            rolePrivilegeSetupService.onApplicationEvent(contextRefreshedEvent);

            // Then
            verify(privilegeRepository, never()).findByName(anyString());
            verify(roleRepository, never()).findByName(anyString());
            assertThat(rolePrivilegeSetupService.isAlreadySetup()).isTrue();
        }

        @Test
        @DisplayName("Should skip null role names in configuration")
        void shouldSkipNullRoleNamesInConfiguration() {
            // Given
            Map<String, List<String>> rolesAndPrivileges = new HashMap<>();
            rolesAndPrivileges.put(null, Arrays.asList("READ_PRIVILEGE"));
            rolesAndPrivileges.put("ROLE_USER", Arrays.asList("READ_PRIVILEGE"));
            
            when(rolesAndPrivilegesConfig.getRolesAndPrivileges()).thenReturn(rolesAndPrivileges);
            when(privilegeRepository.findByName("READ_PRIVILEGE")).thenReturn(new Privilege("READ_PRIVILEGE"));

            // When
            rolePrivilegeSetupService.onApplicationEvent(contextRefreshedEvent);

            // Then
            verify(roleRepository, times(1)).findByName("ROLE_USER");
            verify(roleRepository, never()).findByName(null);
        }

        @Test
        @DisplayName("Should skip roles with null privilege lists")
        void shouldSkipRolesWithNullPrivilegeLists() {
            // Given
            Map<String, List<String>> rolesAndPrivileges = new HashMap<>();
            rolesAndPrivileges.put("ROLE_ADMIN", null);
            rolesAndPrivileges.put("ROLE_USER", Arrays.asList("READ_PRIVILEGE"));
            
            when(rolesAndPrivilegesConfig.getRolesAndPrivileges()).thenReturn(rolesAndPrivileges);
            when(privilegeRepository.findByName("READ_PRIVILEGE")).thenReturn(new Privilege("READ_PRIVILEGE"));

            // When
            rolePrivilegeSetupService.onApplicationEvent(contextRefreshedEvent);

            // Then
            verify(roleRepository, times(1)).findByName("ROLE_USER");
            verify(roleRepository, never()).findByName("ROLE_ADMIN");
        }
    }

    @Nested
    @DisplayName("Privilege Management Tests")
    class PrivilegeManagementTests {

        @Test
        @DisplayName("Should create new privilege when it doesn't exist")
        void shouldCreateNewPrivilegeWhenNotExists() {
            // Given
            String privilegeName = "READ_PRIVILEGE";
            when(privilegeRepository.findByName(privilegeName)).thenReturn(null);
            Privilege savedPrivilege = new Privilege(privilegeName);
            savedPrivilege.setId(1L);
            when(privilegeRepository.save(any(Privilege.class))).thenReturn(savedPrivilege);

            // When
            Privilege result = rolePrivilegeSetupService.getOrCreatePrivilege(privilegeName);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo(privilegeName);
            assertThat(result.getId()).isEqualTo(1L);
            verify(privilegeRepository).findByName(privilegeName);
            verify(privilegeRepository).save(any(Privilege.class));
        }

        @Test
        @DisplayName("Should return existing privilege when it exists")
        void shouldReturnExistingPrivilegeWhenExists() {
            // Given
            String privilegeName = "WRITE_PRIVILEGE";
            Privilege existingPrivilege = new Privilege(privilegeName);
            existingPrivilege.setId(2L);
            when(privilegeRepository.findByName(privilegeName)).thenReturn(existingPrivilege);

            // When
            Privilege result = rolePrivilegeSetupService.getOrCreatePrivilege(privilegeName);

            // Then
            assertThat(result).isEqualTo(existingPrivilege);
            verify(privilegeRepository).findByName(privilegeName);
            verify(privilegeRepository, never()).save(any(Privilege.class));
        }

        @Test
        @DisplayName("Should handle multiple privileges efficiently")
        void shouldHandleMultiplePrivilegesEfficiently() {
            // Given
            List<String> privilegeNames = Arrays.asList("READ", "WRITE", "DELETE", "UPDATE");
            when(privilegeRepository.findByName(anyString())).thenReturn(null);
            when(privilegeRepository.save(any(Privilege.class))).thenAnswer(invocation -> {
                Privilege p = invocation.getArgument(0);
                p.setId((long) privilegeNames.indexOf(p.getName()) + 1);
                return p;
            });

            // When
            Set<Privilege> privileges = new HashSet<>();
            for (String name : privilegeNames) {
                privileges.add(rolePrivilegeSetupService.getOrCreatePrivilege(name));
            }

            // Then
            assertThat(privileges).hasSize(4);
            verify(privilegeRepository, times(4)).findByName(anyString());
            verify(privilegeRepository, times(4)).save(any(Privilege.class));
        }
    }

    @Nested
    @DisplayName("Role Management Tests")
    class RoleManagementTests {

        @Test
        @DisplayName("Should create new role when it doesn't exist")
        void shouldCreateNewRoleWhenNotExists() {
            // Given
            String roleName = "ROLE_ADMIN";
            Set<Privilege> privileges = new HashSet<>();
            privileges.add(new Privilege("READ_PRIVILEGE"));
            privileges.add(new Privilege("WRITE_PRIVILEGE"));
            
            when(roleRepository.findByName(roleName)).thenReturn(null);
            when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> {
                Role r = invocation.getArgument(0);
                r.setId(1L);
                return r;
            });

            // When
            Role result = rolePrivilegeSetupService.getOrCreateRole(roleName, privileges);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo(roleName);
            assertThat(result.getPrivileges()).isEqualTo(privileges);
            verify(roleRepository).findByName(roleName);
            verify(roleRepository).save(any(Role.class));
        }

        @Test
        @DisplayName("Should update existing role's privileges")
        void shouldUpdateExistingRolePrivileges() {
            // Given
            String roleName = "ROLE_USER";
            Role existingRole = new Role(roleName);
            existingRole.setId(2L);
            Set<Privilege> oldPrivileges = new HashSet<>();
            oldPrivileges.add(new Privilege("OLD_PRIVILEGE"));
            existingRole.setPrivileges(oldPrivileges);
            
            Set<Privilege> newPrivileges = new HashSet<>();
            Privilege readPriv = new Privilege("READ_PRIVILEGE");
            readPriv.setId(10L);
            Privilege writePriv = new Privilege("WRITE_PRIVILEGE");
            writePriv.setId(11L);
            newPrivileges.add(readPriv);
            newPrivileges.add(writePriv);
            
            when(roleRepository.findByName(roleName)).thenReturn(existingRole);
            when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            Role result = rolePrivilegeSetupService.getOrCreateRole(roleName, newPrivileges);

            // Then
            // The role's privileges should be completely replaced with the new set
            assertThat(result).isSameAs(existingRole);
            assertThat(result.getPrivileges()).isEqualTo(newPrivileges);
            assertThat(result.getPrivileges()).hasSize(2);
            verify(roleRepository).findByName(roleName);
            verify(roleRepository).save(existingRole);
        }

        @Test
        @DisplayName("Should handle role with empty privilege set")
        void shouldHandleRoleWithEmptyPrivilegeSet() {
            // Given
            String roleName = "ROLE_GUEST";
            Set<Privilege> emptyPrivileges = new HashSet<>();
            
            when(roleRepository.findByName(roleName)).thenReturn(null);
            when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            Role result = rolePrivilegeSetupService.getOrCreateRole(roleName, emptyPrivileges);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo(roleName);
            assertThat(result.getPrivileges()).isEmpty();
            verify(roleRepository).save(any(Role.class));
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("Should handle complex role hierarchy setup")
        void shouldHandleComplexRoleHierarchySetup() {
            // Given
            Map<String, List<String>> rolesAndPrivileges = new HashMap<>();
            rolesAndPrivileges.put("ROLE_SUPER_ADMIN", Arrays.asList("SUPER_READ", "SUPER_WRITE", "SUPER_DELETE"));
            rolesAndPrivileges.put("ROLE_ADMIN", Arrays.asList("READ", "WRITE", "DELETE"));
            rolesAndPrivileges.put("ROLE_MODERATOR", Arrays.asList("READ", "WRITE"));
            rolesAndPrivileges.put("ROLE_USER", Arrays.asList("READ"));
            
            when(rolesAndPrivilegesConfig.getRolesAndPrivileges()).thenReturn(rolesAndPrivileges);
            when(privilegeRepository.findByName(anyString())).thenReturn(null);
            when(privilegeRepository.save(any(Privilege.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(roleRepository.findByName(anyString())).thenReturn(null);
            when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            rolePrivilegeSetupService.onApplicationEvent(contextRefreshedEvent);

            // Then
            // Verify unique privileges are created (7 unique privileges total, but READ appears 4 times)
            ArgumentCaptor<Privilege> privilegeCaptor = ArgumentCaptor.forClass(Privilege.class);
            verify(privilegeRepository, times(9)).save(privilegeCaptor.capture()); // Total privilege saves
            
            // Verify all roles are created
            ArgumentCaptor<Role> roleCaptor = ArgumentCaptor.forClass(Role.class);
            verify(roleRepository, times(4)).save(roleCaptor.capture());
            
            List<Role> savedRoles = roleCaptor.getAllValues();
            assertThat(savedRoles).extracting(Role::getName)
                    .containsExactlyInAnyOrder("ROLE_SUPER_ADMIN", "ROLE_ADMIN", "ROLE_MODERATOR", "ROLE_USER");
        }

        @Test
        @DisplayName("Should reuse existing privileges across roles")
        void shouldReuseExistingPrivilegesAcrossRoles() {
            // Given
            Map<String, List<String>> rolesAndPrivileges = new HashMap<>();
            rolesAndPrivileges.put("ROLE_ADMIN", Arrays.asList("READ", "WRITE"));
            rolesAndPrivileges.put("ROLE_USER", Arrays.asList("READ"));
            
            Privilege readPrivilege = new Privilege("READ");
            readPrivilege.setId(1L);
            
            when(rolesAndPrivilegesConfig.getRolesAndPrivileges()).thenReturn(rolesAndPrivileges);
            // First call returns null (creates), subsequent calls return existing
            when(privilegeRepository.findByName("READ"))
                    .thenReturn(null)
                    .thenReturn(readPrivilege);
            when(privilegeRepository.findByName("WRITE")).thenReturn(null);
            when(privilegeRepository.save(any(Privilege.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(roleRepository.findByName(anyString())).thenReturn(null);
            when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            rolePrivilegeSetupService.onApplicationEvent(contextRefreshedEvent);

            // Then
            // READ privilege should be looked up twice but only created once
            verify(privilegeRepository, times(2)).findByName("READ");
            // WRITE privilege should be created once
            verify(privilegeRepository, times(1)).findByName("WRITE");
            // Only 2 privileges should be saved (READ once, WRITE once)
            verify(privilegeRepository, times(2)).save(any(Privilege.class));
        }

        @Test
        @DisplayName("Should handle database transaction properly")
        void shouldHandleDatabaseTransactionProperly() {
            // Given
            Map<String, List<String>> rolesAndPrivileges = new HashMap<>();
            rolesAndPrivileges.put("ROLE_TRANSACTIONAL", Arrays.asList("TRANSACT_READ", "TRANSACT_WRITE"));
            
            when(rolesAndPrivilegesConfig.getRolesAndPrivileges()).thenReturn(rolesAndPrivileges);
            when(privilegeRepository.findByName(anyString())).thenReturn(null);
            
            // Track created privileges to return the same instance when saved
            Map<String, Privilege> createdPrivileges = new HashMap<>();
            when(privilegeRepository.save(any(Privilege.class))).thenAnswer(invocation -> {
                Privilege p = invocation.getArgument(0);
                p.setId((long) (createdPrivileges.size() + 1)); // Simulate DB generating ID
                createdPrivileges.put(p.getName(), p);
                return p;
            });
            
            when(roleRepository.findByName(anyString())).thenReturn(null);
            when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> {
                Role r = invocation.getArgument(0);
                r.setId(1L);
                return r;
            });

            // When
            rolePrivilegeSetupService.onApplicationEvent(contextRefreshedEvent);

            // Then
            ArgumentCaptor<Role> roleCaptor = ArgumentCaptor.forClass(Role.class);
            verify(roleRepository).save(roleCaptor.capture());
            
            Role savedRole = roleCaptor.getValue();
            // The privileges are stored in a Set, so we should have 2 unique privileges
            assertThat(savedRole.getPrivileges()).hasSize(2);
            // Verify the privileges have their names set correctly
            assertThat(savedRole.getPrivileges())
                    .extracting(Privilege::getName)
                    .containsExactlyInAnyOrder("TRANSACT_READ", "TRANSACT_WRITE");
            // Verify the privileges have IDs assigned
            assertThat(savedRole.getPrivileges()).allMatch(p -> p.getId() != null && p.getId() > 0);
        }

        @Test
        @DisplayName("Should complete setup even with mixed existing and new entities")
        void shouldCompleteSetupWithMixedExistingAndNewEntities() {
            // Given
            Map<String, List<String>> rolesAndPrivileges = new HashMap<>();
            rolesAndPrivileges.put("ROLE_EXISTING", Arrays.asList("EXISTING_PRIV", "NEW_PRIV"));
            rolesAndPrivileges.put("ROLE_NEW", Arrays.asList("EXISTING_PRIV"));
            
            Privilege existingPriv = new Privilege("EXISTING_PRIV");
            existingPriv.setId(100L);
            Role existingRole = new Role("ROLE_EXISTING");
            existingRole.setId(200L);
            
            when(rolesAndPrivilegesConfig.getRolesAndPrivileges()).thenReturn(rolesAndPrivileges);
            when(privilegeRepository.findByName("EXISTING_PRIV")).thenReturn(existingPriv);
            when(privilegeRepository.findByName("NEW_PRIV")).thenReturn(null);
            when(privilegeRepository.save(any(Privilege.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(roleRepository.findByName("ROLE_EXISTING")).thenReturn(existingRole);
            when(roleRepository.findByName("ROLE_NEW")).thenReturn(null);
            when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            rolePrivilegeSetupService.onApplicationEvent(contextRefreshedEvent);

            // Then
            verify(privilegeRepository, times(1)).save(any(Privilege.class)); // Only NEW_PRIV
            verify(roleRepository, times(2)).save(any(Role.class)); // Both roles get saved (existing one with updated privileges)
            assertThat(rolePrivilegeSetupService.isAlreadySetup()).isTrue();
        }
    }
}