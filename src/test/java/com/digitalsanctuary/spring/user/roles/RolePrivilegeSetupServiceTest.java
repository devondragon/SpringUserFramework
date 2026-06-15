package com.digitalsanctuary.spring.user.roles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import org.springframework.dao.DataIntegrityViolationException;

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
        // In production the REQUIRES_NEW insert methods are routed through the Spring proxy. In this unit test there is
        // no proxy, so point the self-reference at the bean itself: a direct call still exercises the catch/re-read
        // branch we care about (the transactional propagation is a runtime concern verified by integration tests).
        rolePrivilegeSetupService.setSelf(rolePrivilegeSetupService);
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
            when(privilegeRepository.saveAndFlush(any(Privilege.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(roleRepository.findByName(anyString())).thenReturn(null);
            when(roleRepository.saveAndFlush(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            rolePrivilegeSetupService.onApplicationEvent(contextRefreshedEvent);

            // Then
            // 4 privilege creations (READ appears for both roles and findByName always returns null, so it is created
            // each time it is requested in getOrCreatePrivilege): 3 for ROLE_ADMIN + 1 for ROLE_USER.
            verify(privilegeRepository, times(4)).saveAndFlush(any(Privilege.class));
            verify(roleRepository, times(2)).findByName(anyString());
            verify(roleRepository, times(2)).saveAndFlush(any(Role.class));
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
            when(roleRepository.findByName("ROLE_USER")).thenReturn(null);
            when(roleRepository.saveAndFlush(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));

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
            when(roleRepository.findByName("ROLE_USER")).thenReturn(null);
            when(roleRepository.saveAndFlush(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));

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
            when(privilegeRepository.saveAndFlush(any(Privilege.class))).thenReturn(savedPrivilege);

            // When
            Privilege result = rolePrivilegeSetupService.getOrCreatePrivilege(privilegeName);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo(privilegeName);
            assertThat(result.getId()).isEqualTo(1L);
            verify(privilegeRepository).findByName(privilegeName);
            verify(privilegeRepository).saveAndFlush(any(Privilege.class));
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
            verify(privilegeRepository, never()).saveAndFlush(any(Privilege.class));
        }

        @Test
        @DisplayName("Should return existing privilege when a concurrent node inserted it (constraint race)")
        void shouldReturnExistingPrivilegeWhenConcurrentInsertViolatesConstraint() {
            // Given: first findByName misses (privilege not yet visible), insert hits the unique constraint because a
            // concurrent node committed it first, then the re-read finds the winning node's row.
            String privilegeName = "READ_PRIVILEGE";
            Privilege concurrentPrivilege = new Privilege(privilegeName);
            concurrentPrivilege.setId(42L);
            when(privilegeRepository.findByName(privilegeName))
                    .thenReturn(null)
                    .thenReturn(concurrentPrivilege);
            when(privilegeRepository.saveAndFlush(any(Privilege.class)))
                    .thenThrow(new DataIntegrityViolationException("unique violation on privilege.name"));

            // When
            Privilege result = rolePrivilegeSetupService.getOrCreatePrivilege(privilegeName);

            // Then: no exception propagated, the existing row is returned, no duplicate created.
            assertThat(result).isSameAs(concurrentPrivilege);
            verify(privilegeRepository, times(2)).findByName(privilegeName);
            verify(privilegeRepository, times(1)).saveAndFlush(any(Privilege.class));
        }

        @Test
        @DisplayName("Should rethrow when insert fails and re-read still finds nothing")
        void shouldRethrowWhenInsertFailsAndReReadFindsNothing() {
            // Given: a genuine integrity problem (not a name race) — the row is still absent after the failed insert.
            String privilegeName = "READ_PRIVILEGE";
            when(privilegeRepository.findByName(privilegeName)).thenReturn(null);
            DataIntegrityViolationException failure = new DataIntegrityViolationException("not a name conflict");
            when(privilegeRepository.saveAndFlush(any(Privilege.class))).thenThrow(failure);

            // When / Then
            try {
                rolePrivilegeSetupService.getOrCreatePrivilege(privilegeName);
                org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown(DataIntegrityViolationException.class);
            } catch (DataIntegrityViolationException e) {
                assertThat(e).isSameAs(failure);
            }
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
            Privilege readPriv = new Privilege("READ_PRIVILEGE");
            readPriv.setId(10L);
            Privilege writePriv = new Privilege("WRITE_PRIVILEGE");
            writePriv.setId(11L);
            when(privilegeRepository.findByName("READ_PRIVILEGE")).thenReturn(readPriv);
            when(privilegeRepository.findByName("WRITE_PRIVILEGE")).thenReturn(writePriv);

            when(roleRepository.findByName(roleName)).thenReturn(null);
            when(roleRepository.saveAndFlush(any(Role.class))).thenAnswer(invocation -> {
                Role r = invocation.getArgument(0);
                r.setId(1L);
                return r;
            });

            // When
            Role result = rolePrivilegeSetupService.getOrCreateRole(roleName, new HashSet<>(Arrays.asList("READ_PRIVILEGE", "WRITE_PRIVILEGE")));

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo(roleName);
            assertThat(result.getPrivileges()).containsExactlyInAnyOrder(readPriv, writePriv);
            verify(roleRepository).findByName(roleName);
            verify(roleRepository).saveAndFlush(any(Role.class));
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

            Privilege readPriv = new Privilege("READ_PRIVILEGE");
            readPriv.setId(10L);
            Privilege writePriv = new Privilege("WRITE_PRIVILEGE");
            writePriv.setId(11L);
            when(privilegeRepository.findByName("READ_PRIVILEGE")).thenReturn(readPriv);
            when(privilegeRepository.findByName("WRITE_PRIVILEGE")).thenReturn(writePriv);

            when(roleRepository.findByName(roleName)).thenReturn(existingRole);
            when(roleRepository.findById(2L)).thenReturn(Optional.of(existingRole));
            when(roleRepository.saveAndFlush(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            Role result = rolePrivilegeSetupService.getOrCreateRole(roleName, new HashSet<>(Arrays.asList("READ_PRIVILEGE", "WRITE_PRIVILEGE")));

            // Then
            // The role's privileges should be completely replaced with the new set
            assertThat(result).isSameAs(existingRole);
            assertThat(result.getPrivileges()).containsExactlyInAnyOrder(readPriv, writePriv);
            assertThat(result.getPrivileges()).hasSize(2);
            verify(roleRepository).findByName(roleName);
            verify(roleRepository).saveAndFlush(existingRole);
        }

        @Test
        @DisplayName("Should handle role with empty privilege set")
        void shouldHandleRoleWithEmptyPrivilegeSet() {
            // Given
            String roleName = "ROLE_GUEST";

            when(roleRepository.findByName(roleName)).thenReturn(null);
            when(roleRepository.saveAndFlush(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            Role result = rolePrivilegeSetupService.getOrCreateRole(roleName, new HashSet<>());

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo(roleName);
            assertThat(result.getPrivileges()).isEmpty();
            verify(roleRepository).saveAndFlush(any(Role.class));
        }

        @Test
        @DisplayName("Should return existing role when a concurrent node inserted it (constraint race)")
        void shouldReturnExistingRoleWhenConcurrentInsertViolatesConstraint() {
            // Given: role not visible on first read, insert hits the unique constraint (concurrent node won), re-read
            // finds the winning row, and we (re)assign privileges onto it.
            String roleName = "ROLE_ADMIN";
            Privilege readPriv = new Privilege("READ_PRIVILEGE");
            when(privilegeRepository.findByName("READ_PRIVILEGE")).thenReturn(readPriv);

            Role concurrentRole = new Role(roleName);
            concurrentRole.setId(99L);

            when(roleRepository.findByName(roleName))
                    .thenReturn(null)
                    .thenReturn(concurrentRole);
            when(roleRepository.findById(99L)).thenReturn(Optional.of(concurrentRole));
            when(roleRepository.saveAndFlush(any(Role.class)))
                    .thenThrow(new DataIntegrityViolationException("unique violation on role.name"))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            Role result = rolePrivilegeSetupService.getOrCreateRole(roleName, new HashSet<>(Arrays.asList("READ_PRIVILEGE")));

            // Then: no exception propagated, the existing row is returned with the desired privileges.
            assertThat(result).isSameAs(concurrentRole);
            assertThat(result.getPrivileges()).containsExactly(readPriv);
            verify(roleRepository, times(2)).findByName(roleName);
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        /**
         * Wires the mocked repositories to behave like a simple in-memory store: {@code findByName} returns whatever
         * was previously {@code saveAndFlush}-ed (assigning an id on first save), and {@code findById} returns the
         * stored row. This lets the role insert/update path resolve privileges to "managed" instances by name, mirroring
         * production where the privileges were created earlier in the same setup flow.
         */
        private void wireInMemoryStore() {
            final Map<String, Privilege> privileges = new HashMap<>();
            final Map<String, Role> roles = new HashMap<>();
            final Map<Long, Role> rolesById = new HashMap<>();

            lenient().when(privilegeRepository.findByName(anyString())).thenAnswer(inv -> privileges.get(inv.getArgument(0)));
            lenient().when(privilegeRepository.saveAndFlush(any(Privilege.class))).thenAnswer(inv -> {
                Privilege p = inv.getArgument(0);
                if (p.getId() == null) {
                    p.setId((long) (privileges.size() + 1));
                }
                privileges.put(p.getName(), p);
                return p;
            });
            lenient().when(roleRepository.findByName(anyString())).thenAnswer(inv -> roles.get(inv.getArgument(0)));
            lenient().when(roleRepository.findById(any())).thenAnswer(inv -> Optional.ofNullable(rolesById.get(inv.getArgument(0))));
            lenient().when(roleRepository.saveAndFlush(any(Role.class))).thenAnswer(inv -> {
                Role r = inv.getArgument(0);
                if (r.getId() == null) {
                    r.setId((long) (roles.size() + 1));
                }
                roles.put(r.getName(), r);
                rolesById.put(r.getId(), r);
                return r;
            });
        }

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
            wireInMemoryStore();

            // When
            rolePrivilegeSetupService.onApplicationEvent(contextRefreshedEvent);

            // Then
            // 6 unique privileges are persisted exactly once each (READ/WRITE/DELETE are shared across roles).
            ArgumentCaptor<Privilege> privilegeCaptor = ArgumentCaptor.forClass(Privilege.class);
            verify(privilegeRepository, times(6)).saveAndFlush(privilegeCaptor.capture());
            assertThat(privilegeCaptor.getAllValues()).extracting(Privilege::getName)
                    .containsExactlyInAnyOrder("SUPER_READ", "SUPER_WRITE", "SUPER_DELETE", "READ", "WRITE", "DELETE");

            // All 4 roles are created with their resolved privileges.
            ArgumentCaptor<Role> roleCaptor = ArgumentCaptor.forClass(Role.class);
            verify(roleRepository, times(4)).saveAndFlush(roleCaptor.capture());
            assertThat(roleCaptor.getAllValues()).extracting(Role::getName)
                    .containsExactlyInAnyOrder("ROLE_SUPER_ADMIN", "ROLE_ADMIN", "ROLE_MODERATOR", "ROLE_USER");
        }

        @Test
        @DisplayName("Should reuse existing privileges across roles")
        void shouldReuseExistingPrivilegesAcrossRoles() {
            // Given
            Map<String, List<String>> rolesAndPrivileges = new HashMap<>();
            rolesAndPrivileges.put("ROLE_ADMIN", Arrays.asList("READ", "WRITE"));
            rolesAndPrivileges.put("ROLE_USER", Arrays.asList("READ"));

            when(rolesAndPrivilegesConfig.getRolesAndPrivileges()).thenReturn(rolesAndPrivileges);
            wireInMemoryStore();

            // When
            rolePrivilegeSetupService.onApplicationEvent(contextRefreshedEvent);

            // Then
            // READ and WRITE are each created exactly once; READ is reused for the second role.
            ArgumentCaptor<Privilege> privilegeCaptor = ArgumentCaptor.forClass(Privilege.class);
            verify(privilegeRepository, times(2)).saveAndFlush(privilegeCaptor.capture());
            assertThat(privilegeCaptor.getAllValues()).extracting(Privilege::getName)
                    .containsExactlyInAnyOrder("READ", "WRITE");
        }

        @Test
        @DisplayName("Should handle database transaction properly")
        void shouldHandleDatabaseTransactionProperly() {
            // Given
            Map<String, List<String>> rolesAndPrivileges = new HashMap<>();
            rolesAndPrivileges.put("ROLE_TRANSACTIONAL", Arrays.asList("TRANSACT_READ", "TRANSACT_WRITE"));

            when(rolesAndPrivilegesConfig.getRolesAndPrivileges()).thenReturn(rolesAndPrivileges);
            wireInMemoryStore();

            // When
            rolePrivilegeSetupService.onApplicationEvent(contextRefreshedEvent);

            // Then
            ArgumentCaptor<Role> roleCaptor = ArgumentCaptor.forClass(Role.class);
            verify(roleRepository).saveAndFlush(roleCaptor.capture());

            Role savedRole = roleCaptor.getValue();
            // The privileges are stored in a Set, so we should have 2 unique privileges
            assertThat(savedRole.getPrivileges()).hasSize(2);
            assertThat(savedRole.getPrivileges())
                    .extracting(Privilege::getName)
                    .containsExactlyInAnyOrder("TRANSACT_READ", "TRANSACT_WRITE");
            // Verify the privileges have IDs assigned (they were resolved as persisted, managed entities)
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
            // Pre-seed an in-memory store with one existing privilege and one existing role.
            final Map<String, Privilege> privileges = new HashMap<>();
            privileges.put("EXISTING_PRIV", existingPriv);
            final Map<String, Role> roles = new HashMap<>();
            roles.put("ROLE_EXISTING", existingRole);
            final Map<Long, Role> rolesById = new HashMap<>();
            rolesById.put(200L, existingRole);

            when(privilegeRepository.findByName(anyString())).thenAnswer(inv -> privileges.get(inv.getArgument(0)));
            when(privilegeRepository.saveAndFlush(any(Privilege.class))).thenAnswer(inv -> {
                Privilege p = inv.getArgument(0);
                if (p.getId() == null) {
                    p.setId((long) (privileges.size() + 1));
                }
                privileges.put(p.getName(), p);
                return p;
            });
            when(roleRepository.findByName(anyString())).thenAnswer(inv -> roles.get(inv.getArgument(0)));
            when(roleRepository.findById(any())).thenAnswer(inv -> Optional.ofNullable(rolesById.get(inv.getArgument(0))));
            when(roleRepository.saveAndFlush(any(Role.class))).thenAnswer(inv -> {
                Role r = inv.getArgument(0);
                if (r.getId() == null) {
                    r.setId((long) (roles.size() + 1));
                }
                roles.put(r.getName(), r);
                rolesById.put(r.getId(), r);
                return r;
            });

            // When
            rolePrivilegeSetupService.onApplicationEvent(contextRefreshedEvent);

            // Then
            verify(privilegeRepository, times(1)).saveAndFlush(any(Privilege.class)); // Only NEW_PRIV created
            verify(roleRepository, times(2)).saveAndFlush(any(Role.class)); // Both roles saved (existing one updated)
            verify(roleRepository, times(1)).findById(200L); // existing role re-read via REQUIRES_NEW update path
            assertThat(rolePrivilegeSetupService.isAlreadySetup()).isTrue();
        }
    }
}
