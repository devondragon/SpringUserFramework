package com.digitalsanctuary.spring.user.test.builders;

import com.digitalsanctuary.spring.user.persistence.model.Privilege;
import com.digitalsanctuary.spring.user.persistence.model.Role;
import com.digitalsanctuary.spring.user.persistence.model.User;

import java.util.HashSet;
import java.util.Set;

/**
 * Fluent builder for creating test Role entities with sensible defaults.
 * This builder simplifies role creation for tests and ensures consistent test data.
 * 
 * Example usage:
 * <pre>
 * Role adminRole = RoleTestDataBuilder.aRole()
 *     .withName("ROLE_ADMIN")
 *     .withDescription("Administrator role")
 *     .withPrivilege("READ_PRIVILEGE")
 *     .withPrivilege("WRITE_PRIVILEGE")
 *     .build();
 * </pre>
 */
public class RoleTestDataBuilder {
    
    private static long idCounter = 1L;
    
    private Long id;
    private String name;
    private String description;
    private Set<User> users = new HashSet<>();
    private Set<Privilege> privileges = new HashSet<>();

    private RoleTestDataBuilder() {
        this.id = idCounter++;
        this.name = "ROLE_USER"; // Default role
        this.description = "Default user role";
    }

    /**
     * Creates a new RoleTestDataBuilder instance.
     */
    public static RoleTestDataBuilder aRole() {
        return new RoleTestDataBuilder();
    }

    /**
     * Creates a builder for a default user role.
     */
    public static RoleTestDataBuilder aUserRole() {
        return new RoleTestDataBuilder()
                .withName("ROLE_USER")
                .withDescription("Standard user role")
                .withPrivilege("READ_PRIVILEGE");
    }

    /**
     * Creates a builder for an admin role.
     */
    public static RoleTestDataBuilder anAdminRole() {
        return new RoleTestDataBuilder()
                .withName("ROLE_ADMIN")
                .withDescription("Administrator role")
                .withPrivilege("READ_PRIVILEGE")
                .withPrivilege("WRITE_PRIVILEGE")
                .withPrivilege("DELETE_PRIVILEGE")
                .withPrivilege("ADMIN_PRIVILEGE");
    }

    /**
     * Creates a builder for a moderator role.
     */
    public static RoleTestDataBuilder aModeratorRole() {
        return new RoleTestDataBuilder()
                .withName("ROLE_MODERATOR")
                .withDescription("Moderator role")
                .withPrivilege("READ_PRIVILEGE")
                .withPrivilege("WRITE_PRIVILEGE")
                .withPrivilege("MODERATE_PRIVILEGE");
    }

    /**
     * Creates a builder for a guest role.
     */
    public static RoleTestDataBuilder aGuestRole() {
        return new RoleTestDataBuilder()
                .withName("ROLE_GUEST")
                .withDescription("Guest role with limited access")
                .withPrivilege("READ_PRIVILEGE");
    }

    public RoleTestDataBuilder withId(Long id) {
        this.id = id;
        return this;
    }

    public RoleTestDataBuilder withName(String name) {
        this.name = name;
        return this;
    }

    public RoleTestDataBuilder withDescription(String description) {
        this.description = description;
        return this;
    }

    public RoleTestDataBuilder withUser(User user) {
        this.users.add(user);
        return this;
    }

    public RoleTestDataBuilder withUsers(Set<User> users) {
        this.users = new HashSet<>(users);
        return this;
    }

    public RoleTestDataBuilder withPrivilege(String privilegeName) {
        Privilege privilege = new Privilege();
        privilege.setId((long) privilegeName.hashCode());
        privilege.setName(privilegeName);
        this.privileges.add(privilege);
        return this;
    }

    public RoleTestDataBuilder withPrivilege(Privilege privilege) {
        this.privileges.add(privilege);
        return this;
    }

    public RoleTestDataBuilder withPrivileges(Set<Privilege> privileges) {
        this.privileges = new HashSet<>(privileges);
        return this;
    }

    public RoleTestDataBuilder withPrivileges(String... privilegeNames) {
        for (String privilegeName : privilegeNames) {
            withPrivilege(privilegeName);
        }
        return this;
    }

    /**
     * Clears all privileges from this role.
     */
    public RoleTestDataBuilder withNoPrivileges() {
        this.privileges.clear();
        return this;
    }

    /**
     * Builds the Role entity with all configured values.
     */
    public Role build() {
        Role role = new Role();
        role.setId(id);
        role.setName(name);
        role.setDescription(description);
        role.setUsers(users);
        role.setPrivileges(privileges);
        
        return role;
    }

    /**
     * Builds a set containing the configured role.
     * Useful for methods expecting a set of roles.
     */
    public Set<Role> buildAsSet() {
        return Set.of(build());
    }

    /**
     * Creates a standard set of roles (USER, ADMIN, MODERATOR).
     */
    public static Set<Role> buildStandardRoles() {
        return Set.of(
            aUserRole().build(),
            anAdminRole().build(),
            aModeratorRole().build()
        );
    }
}