package com.digitalsanctuary.spring.user.service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;
import com.digitalsanctuary.spring.user.persistence.model.Privilege;
import com.digitalsanctuary.spring.user.persistence.model.Role;
import com.digitalsanctuary.spring.user.persistence.repository.PrivilegeRepository;
import com.digitalsanctuary.spring.user.persistence.repository.RoleRepository;
import com.digitalsanctuary.spring.user.util.RolesAndPrivilegesConfig;
import jakarta.transaction.Transactional;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * A service to set up roles and privileges from a configuration when the application starts.
 */
@Slf4j
@Data
@RequiredArgsConstructor
@Component
public class RolePrivilegeSetupService implements ApplicationListener<ContextRefreshedEvent> {

    /** The already setup flag. */
    private boolean alreadySetup = false;

    /** The roles and privileges configuration. */
    private final RolesAndPrivilegesConfig rolesAndPrivilegesConfig;

    /** The role repository. */
    private final RoleRepository roleRepository;

    /** The privilege repository. */
    private final PrivilegeRepository privilegeRepository;

    /**
     * Triggered when the application context is refreshed.
     *
     * @param event the context refreshed event
     */
    @Override
    @Transactional
    public void onApplicationEvent(final ContextRefreshedEvent event) {
        if (alreadySetup) {
            return;
        }

        log.debug("rolesAndPrivilegesConfig: {}", rolesAndPrivilegesConfig);

        for (Map.Entry<String, List<String>> entry : rolesAndPrivilegesConfig.getRolesAndPrivileges().entrySet()) {
            final String roleName = entry.getKey();
            final List<String> privileges = entry.getValue();
            if (roleName != null && privileges != null) {
                final Set<Privilege> privilegeSet = new HashSet<>();
                for (String privilegeName : privileges) {
                    privilegeSet.add(getOrCreatePrivilege(privilegeName));
                }
                getOrCreateRole(roleName, privilegeSet);
            }
        }
        alreadySetup = true;
    }

    /**
     * Retrieves or creates a privilege by name.
     *
     * @param name the name of the privilege
     * @return the privilege
     */
    @Transactional
    Privilege getOrCreatePrivilege(final String name) {
        Privilege privilege = privilegeRepository.findByName(name);
        if (privilege == null) {
            privilege = new Privilege(name);
            privilege = privilegeRepository.save(privilege);
        }
        return privilege;
    }

    /**
     * Retrieves or creates a role by name and privileges.
     *
     * @param name the name of the role
     * @param privileges the set of privileges associated with the role
     * @return the role
     */
    @Transactional
    Role getOrCreateRole(final String name, final Set<Privilege> privileges) {
        Role role = roleRepository.findByName(name);
        if (role == null) {
            role = new Role(name);
        }
        role.setPrivileges(privileges);
        role = roleRepository.save(role);
        return role;
    }
}
