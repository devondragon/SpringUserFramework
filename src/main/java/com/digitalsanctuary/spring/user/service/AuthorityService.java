package com.digitalsanctuary.spring.user.service;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.digitalsanctuary.spring.user.persistence.model.Privilege;
import com.digitalsanctuary.spring.user.persistence.model.Role;
import com.digitalsanctuary.spring.user.persistence.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for generating Spring Security {@link GrantedAuthority} objects from user roles and privileges.
 *
 * <p>This service converts the application's role-based permission model into Spring Security's
 * authority-based model by extracting privilege names from assigned roles.</p>
 *
 * @see GrantedAuthority
 * @see Role
 * @see Privilege
 */
@Slf4j
@RequiredArgsConstructor
@Service
@Transactional
public class AuthorityService {

    /**
     * Generates the list of authorities for the given user from their roles and privileges.
     *
     * @param user The user whose authorities to generate.
     * @return The list of authorities for the user.
     */
    public Collection<? extends GrantedAuthority> getAuthoritiesFromUser(User user) {
        return getAuthoritiesFromRoles(user.getRoles());
    }

    /**
     * Returns a collection of Spring Security's {@link GrantedAuthority} objects that includes both the role names and
     * the privilege names associated with the given collection of roles.
     *
     * <p>Including role names as authorities is required for Spring Security's {@code hasRole()} checks
     * (e.g., {@code @PreAuthorize("hasRole('ADMIN')")}) to work correctly.</p>
     *
     * @param roles a collection of roles whose names and privileges should be converted into GrantedAuthority objects
     * @return a deduplicated set of GrantedAuthority objects containing both role names and privilege names
     */
    public Collection<? extends GrantedAuthority> getAuthoritiesFromRoles(Collection<Role> roles) {
        Set<GrantedAuthority> authorities = new HashSet<>();
        for (Role role : roles) {
            authorities.add(new SimpleGrantedAuthority(role.getName()));
            for (Privilege privilege : role.getPrivileges()) {
                authorities.add(new SimpleGrantedAuthority(privilege.getName()));
            }
        }
        return authorities;
    }

}
