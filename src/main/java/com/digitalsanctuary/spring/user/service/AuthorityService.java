package com.digitalsanctuary.spring.user.service;

import java.util.Collection;
import java.util.stream.Collectors;
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
 * The AuthorityService class provides helper methods for generating Spring Security's GrantedAuthority objects from a collection of roles and
 * privileges.
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
     *
     * Returns a collection of Spring Security's GrantedAuthority objects that corresponds to the privileges associated with the given collection of
     * roles.
     *
     * @param roles a collection of roles whose privileges should be converted into Spring Security's GrantedAuthority objects
     * @return a collection of Spring Security's GrantedAuthority objects that corresponds to the privileges associated with the given collection of
     *         roles
     */
    public Collection<? extends GrantedAuthority> getAuthoritiesFromRoles(Collection<Role> roles) {
        // flatMap streams the roles, and maps each Role to its privileges (a Collection of Privilege objects).
        // The stream of Collection<Privilege> objects is then flattened into a single stream of Privilege objects.
        // Finally, each Privilege is mapped to its name as a String, wrapped in a SimpleGrantedAuthority object,
        // and collected into a Set of GrantedAuthority objects.
        return roles.stream().flatMap(role -> role.getPrivileges().stream()).map(Privilege::getName).map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());
    }

}
