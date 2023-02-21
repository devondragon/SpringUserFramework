package com.digitalsanctuary.spring.user.service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
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
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
@Component
public class RolePrivilegeSetupService implements ApplicationListener<ContextRefreshedEvent> {
    private boolean alreadySetup = false;

    @Autowired
    private RolesAndPrivilegesConfig rolesAndPrivilegesConfig;


    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PrivilegeRepository privilegeRepository;


    @Override
    @Transactional
    public void onApplicationEvent(final ContextRefreshedEvent event) {
        if (alreadySetup) {
            return;
        }
        log.debug("rolesAndPrivilegesConfig: " + rolesAndPrivilegesConfig);

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

    @Transactional
    Privilege getOrCreatePrivilege(final String name) {
        Privilege privilege = privilegeRepository.findByName(name);
        if (privilege == null) {
            privilege = new Privilege(name);
            privilege = privilegeRepository.save(privilege);
        }
        return privilege;
    }

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
