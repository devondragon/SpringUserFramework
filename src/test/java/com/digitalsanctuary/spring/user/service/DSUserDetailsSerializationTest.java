package com.digitalsanctuary.spring.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.digitalsanctuary.spring.user.persistence.model.Privilege;
import com.digitalsanctuary.spring.user.persistence.model.Role;
import com.digitalsanctuary.spring.user.persistence.model.User;

/**
 * Verifies that the authenticated principal graph stored in the HTTP session is serializable.
 *
 * <p>This is required for distributed/persistent session stores (e.g. Spring Session JDBC/Redis),
 * where the {@link DSUserDetails} principal and its reachable object graph ({@link User} ->
 * {@link Role} -> {@link Privilege}) must round-trip through Java serialization.</p>
 */
@DisplayName("DSUserDetails Serialization Tests")
class DSUserDetailsSerializationTest {

    /**
     * Builds a fully-populated, eagerly-initialized principal and asserts it survives a Java
     * serialization round-trip with its key fields intact. This exercises the entire reachable
     * object graph (User -> Role -> Privilege), proving the session-stored principal is serializable.
     */
    @Test
    @DisplayName("Should round-trip DSUserDetails principal graph through Java serialization")
    void shouldSerializeAndDeserializePrincipalGraph() throws Exception {
        Privilege readPrivilege = new Privilege("READ_PRIVILEGE", "Read access");
        readPrivilege.setId(100L);

        Role userRole = new Role("ROLE_USER", "Standard user");
        userRole.setId(10L);
        userRole.setPrivileges(Set.of(readPrivilege));

        User user = new User();
        user.setId(1L);
        user.setEmail("serialize@test.com");
        user.setFirstName("Serial");
        user.setLastName("Izable");
        user.setEnabled(true);
        user.setRolesAsSet(Set.of(userRole));

        GrantedAuthority authority = new SimpleGrantedAuthority("ROLE_USER");
        DSUserDetails principal = new DSUserDetails(user, List.of(authority));

        byte[] bytes;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(principal);
            oos.flush();
            bytes = baos.toByteArray();
        }

        DSUserDetails roundTripped;
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            roundTripped = (DSUserDetails) ois.readObject();
        }

        assertThat(roundTripped).isNotNull();
        assertThat(roundTripped.getUsername()).isEqualTo("serialize@test.com");
        assertThat(roundTripped.getUser().getEmail()).isEqualTo("serialize@test.com");
        assertThat(roundTripped.getUser().getFirstName()).isEqualTo("Serial");
        assertThat(roundTripped.isEnabled()).isTrue();
        assertThat(roundTripped.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_USER");
        assertThat(roundTripped.getUser().getRoles()).extracting(Role::getName).containsExactly("ROLE_USER");
        assertThat(roundTripped.getUser().getRolesAsSet().iterator().next().getPrivileges()).extracting(Privilege::getName)
                .containsExactly("READ_PRIVILEGE");
    }
}
