package com.digitalsanctuary.spring.user.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import javax.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;
import com.digitalsanctuary.spring.user.persistence.model.Privilege;
import com.digitalsanctuary.spring.user.persistence.model.Role;
import com.digitalsanctuary.spring.user.persistence.repository.PrivilegeRepository;
import com.digitalsanctuary.spring.user.persistence.repository.RoleRepository;
import com.digitalsanctuary.spring.user.util.RolesAndPrivilegesConfig;
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
                final List<Privilege> privilegesList = new ArrayList<Privilege>();
                for (String privilegeName : privileges) {
                    privilegesList.add(createPrivilegeIfNotFound(privilegeName));
                }
                createRoleIfNotFound(roleName, privilegesList);
            }

        }
        alreadySetup = true;
    }

    @Transactional
    Privilege createPrivilegeIfNotFound(final String name) {
        Privilege privilege = privilegeRepository.findByName(name);
        if (privilege == null) {
            privilege = new Privilege(name);
            privilege = privilegeRepository.save(privilege);
        }
        return privilege;
    }

    @Transactional
    Role createRoleIfNotFound(final String name, final Collection<Privilege> privileges) {
        Role role = roleRepository.findByName(name);
        if (role == null) {
            role = new Role(name);
        }
        role.setPrivileges(privileges);
        role = roleRepository.save(role);
        return role;
    }
}
