package com.digitalsanctuary.spring.user.roles;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.digitalsanctuary.spring.user.persistence.model.Privilege;
import com.digitalsanctuary.spring.user.persistence.model.Role;
import com.digitalsanctuary.spring.user.persistence.repository.PrivilegeRepository;
import com.digitalsanctuary.spring.user.persistence.repository.RoleRepository;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * Service that initializes roles and privileges from configuration on application startup.
 * <p>
 * Listens for {@link ContextRefreshedEvent} and creates or updates roles and privileges in the database based on the
 * {@link RolesAndPrivilegesConfig} settings. This ensures that the configured role/privilege structure exists before
 * the application handles requests.
 * </p>
 * <p>
 * The setup is idempotent and safe under concurrent multi-node startup. Because {@code role.name} and
 * {@code privilege.name} carry a UNIQUE constraint, a second node inserting the same name will fail with a
 * {@link DataIntegrityViolationException}. Each insert runs in its own {@link Propagation#REQUIRES_NEW} transaction
 * (routed through the Spring proxy via the {@link #self} reference) so that a failed insert rolls back only that inner
 * transaction without poisoning the surrounding flow; the catch handler then re-reads the row the winning node created,
 * giving first-writer-wins convergence.
 * </p>
 */
@Slf4j
@Getter
@Component("dsRolePrivilegeSetupService")
public class RolePrivilegeSetupService implements ApplicationListener<ContextRefreshedEvent> {

    /** The already setup flag. {@code volatile} so the guard is visible across threads under concurrent context refresh (e.g. parallel test execution). */
    @Setter
    private volatile boolean alreadySetup = false;

    /** The roles and privileges configuration. */
    private final RolesAndPrivilegesConfig rolesAndPrivilegesConfig;

    /** The role repository. */
    private final RoleRepository roleRepository;

    /** The privilege repository. */
    private final PrivilegeRepository privilegeRepository;

    /**
     * Self-reference used to route the per-entity insert through the Spring proxy so its
     * {@link Propagation#REQUIRES_NEW} boundary takes effect. A direct {@code this.} call would bypass the proxy and run
     * in the caller's transaction (Spring self-invocation limitation). Injected {@code @Lazy} to avoid a circular
     * dependency during bean creation.
     */
    @Lazy
    @Autowired
    @Setter
    private RolePrivilegeSetupService self;

    /**
     * Constructs the setup service.
     *
     * @param rolesAndPrivilegesConfig the roles and privileges configuration
     * @param roleRepository the role repository
     * @param privilegeRepository the privilege repository
     */
    public RolePrivilegeSetupService(final RolesAndPrivilegesConfig rolesAndPrivilegesConfig, final RoleRepository roleRepository,
            final PrivilegeRepository privilegeRepository) {
        this.rolesAndPrivilegesConfig = rolesAndPrivilegesConfig;
        this.roleRepository = roleRepository;
        this.privilegeRepository = privilegeRepository;
    }

    /**
     * Triggered when the application context is refreshed.
     * <p>
     * Intentionally <strong>not</strong> {@code @Transactional}: each {@code getOrCreate} call manages its own
     * transaction boundary so that a unique-constraint violation on one insert cannot mark a shared transaction
     * rollback-only and poison the subsequent re-read.
     * </p>
     *
     * @param event the context refreshed event
     */
    @Override
    public void onApplicationEvent(final ContextRefreshedEvent event) {
        if (alreadySetup) {
            return;
        }

        log.debug("rolesAndPrivilegesConfig: {}", rolesAndPrivilegesConfig);

        for (Map.Entry<String, List<String>> entry : rolesAndPrivilegesConfig.getRolesAndPrivileges().entrySet()) {
            final String roleName = entry.getKey();
            final List<String> privileges = entry.getValue();
            if (roleName != null && privileges != null) {
                for (String privilegeName : privileges) {
                    if (privilegeName != null && !privilegeName.isBlank()) {
                        getOrCreatePrivilege(privilegeName);
                    } else {
                        log.warn("RolePrivilegeSetupService: skipping null/blank privilege name in role '{}'", roleName);
                    }
                }
                // Pass only non-null/non-blank privilege names to the role, matching what was persisted above.
                Set<String> validPrivileges = privileges.stream()
                        .filter(p -> p != null && !p.isBlank())
                        .collect(java.util.stream.Collectors.toSet());
                getOrCreateRole(roleName, validPrivileges);
            }
        }
        alreadySetup = true;
    }

    /**
     * Retrieves or creates a privilege by name, tolerating concurrent creation by another node.
     * <p>
     * If the privilege does not yet exist it is inserted in its own {@link Propagation#REQUIRES_NEW} transaction. If a
     * concurrent node has already inserted a privilege with the same name, the insert fails with a
     * {@link DataIntegrityViolationException}; that inner transaction is rolled back and the row created by the winning
     * node is re-read and returned. The exception is never propagated to the caller.
     * </p>
     *
     * @param name the name of the privilege
     * @return the existing or newly created privilege
     */
    public Privilege getOrCreatePrivilege(final String name) {
        Privilege privilege = privilegeRepository.findByName(name);
        if (privilege != null) {
            return privilege;
        }
        try {
            return self.insertPrivilege(name);
        } catch (final DataIntegrityViolationException e) {
            log.debug("Privilege '{}' was created concurrently; re-reading existing row.", name);
            final Privilege existing = privilegeRepository.findByName(name);
            if (existing == null) {
                throw e;
            }
            return existing;
        }
    }

    /**
     * Inserts a new privilege in its own transaction. Must be {@code public} so the Spring proxy can advise it when
     * invoked through {@link #self}; a package-private/private target is not overridden by the CGLIB proxy subclass and
     * the {@link Propagation#REQUIRES_NEW} boundary would not take effect.
     *
     * @param name the name of the privilege
     * @return the persisted privilege
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Privilege insertPrivilege(final String name) {
        return privilegeRepository.saveAndFlush(new Privilege(name));
    }

    /**
     * Retrieves or creates a role by name and (re)assigns its privileges, tolerating concurrent creation by another
     * node.
     * <p>
     * If the role does not yet exist it is inserted in its own {@link Propagation#REQUIRES_NEW} transaction. If a
     * concurrent node has already inserted a role with the same name, the insert fails with a
     * {@link DataIntegrityViolationException}; that inner transaction is rolled back and the existing row is re-read,
     * has its privileges (re)assigned, and is saved. The exception is never propagated to the caller.
     * </p>
     *
     * @param name the name of the role
     * @param privilegeNames the names of the privileges associated with the role (resolved to managed entities inside
     *        the insert/update transaction)
     * @return the existing or newly created role
     */
    public Role getOrCreateRole(final String name, final Set<String> privilegeNames) {
        final Role existing = roleRepository.findByName(name);
        if (existing != null) {
            return self.updateRolePrivileges(existing.getId(), privilegeNames);
        }
        try {
            return self.insertRole(name, privilegeNames);
        } catch (final DataIntegrityViolationException e) {
            log.debug("Role '{}' was created concurrently; re-reading existing row.", name);
            final Role concurrent = roleRepository.findByName(name);
            if (concurrent == null) {
                throw e;
            }
            return self.updateRolePrivileges(concurrent.getId(), privilegeNames);
        }
    }

    /**
     * Inserts a new role with the given privileges in its own transaction. The privileges are resolved to managed
     * entities by name within this transaction so the role's cascade does not attempt to persist detached instances.
     * Must be {@code public} so the Spring proxy can advise it when invoked through {@link #self} (see
     * {@link #insertPrivilege(String)}).
     *
     * @param name the name of the role
     * @param privilegeNames the names of the privileges to assign
     * @return the persisted role
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Role insertRole(final String name, final Set<String> privilegeNames) {
        final Role role = new Role(name);
        role.setPrivileges(resolvePrivileges(privilegeNames));
        return roleRepository.saveAndFlush(role);
    }

    /**
     * Re-reads an existing role by id and (re)assigns its privileges in its own transaction. The privileges are
     * resolved to managed entities by name within this transaction. Must be {@code public} so the Spring proxy can
     * advise it when invoked through {@link #self} (see {@link #insertPrivilege(String)}).
     *
     * @param roleId the id of the role to update
     * @param privilegeNames the names of the privileges to assign
     * @return the updated role
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Role updateRolePrivileges(final Long roleId, final Set<String> privilegeNames) {
        final Role role = roleRepository.findById(roleId).orElseThrow();
        role.setPrivileges(resolvePrivileges(privilegeNames));
        return roleRepository.saveAndFlush(role);
    }

    /**
     * Resolves a set of privilege names to managed {@link Privilege} entities loaded in the current transaction. Names
     * with no matching row are skipped (they were already created by {@link #getOrCreatePrivilege(String)} earlier in
     * the setup flow, so a miss here only happens transiently and is harmless).
     *
     * @param privilegeNames the privilege names to resolve
     * @return the managed privileges
     */
    private Set<Privilege> resolvePrivileges(final Set<String> privilegeNames) {
        final Set<Privilege> privileges = new HashSet<>();
        for (final String privilegeName : privilegeNames) {
            final Privilege privilege = privilegeRepository.findByName(privilegeName);
            if (privilege != null) {
                privileges.add(privilege);
            } else {
                log.warn("Configured privilege '{}' was not found and will be absent from the role's privilege set. "
                        + "This is usually a typo in the user.roles-and-privileges configuration, or a transient miss "
                        + "during concurrent multi-node startup.", privilegeName);
            }
        }
        return privileges;
    }
}
