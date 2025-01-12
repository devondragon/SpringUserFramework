/**
 * The {@code RolesAndPrivilegesConfig} class is a configuration class used to load user roles and privileges from application properties.
 * <p>
 * The class defines two properties:
 * <ul>
 * <li>{@code rolesAndPrivileges}: a map of roles and their associated privileges</li>
 * <li>{@code roleHierarchy}: a list of role relationships defining the hierarchy of roles</li>
 * </ul>
 * <p>
 * The {@code getRoleHierarchyString()} method is provided to generate a formatted string representation of the role hierarchy.
 *
 * @author Devon Hillard
 * @version 1.0
 * @since 1.0
 */
package com.digitalsanctuary.spring.user.roles;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * The RolesAndPrivilegesConfig class is a Spring Boot configuration class that provides properties for configuring user roles and privileges. This
 * class is used to define properties that control the behavior of the user roles and privileges, such as the role hierarchy and the mapping of roles
 * to privileges.
 */
@Slf4j
@Data
@Component
@PropertySource("classpath:config/dsspringuserconfig.properties")
@ConfigurationProperties(prefix = "user.roles")
public class RolesAndPrivilegesConfig {
    /**
     * The roles and privileges map. This map defines the roles and their associated privileges. The map is structured as follows:
     * <ul>
     * <li>Key: the role name</li>
     * <li>Value: a list of privilege names</li>
     * </ul>
     *
     */
    private Map<String, List<String>> rolesAndPrivileges = new HashMap<>();

    /**
     * The role hierarchy list. This list defines the hierarchy of roles. Each role relationship is defined as a string in the format
     * {@code "role1 > role2"}, where {@code role1} is the parent role and {@code role2} is the child role.
     */
    private List<String> roleHierarchy = new ArrayList<>();

    /**
     * Returns a formatted string representation of the role hierarchy, where each role relationship is separated by a newline character. Returns
     * {@code null} if the role hierarchy is empty or {@code null}.
     *
     * @return a formatted string representation of the role hierarchy, or {@code null} if the hierarchy is empty or {@code null}
     */
    public String getRoleHierarchyString() {
        log.info("roleHierarchy: {}", roleHierarchy);
        if (roleHierarchy == null || roleHierarchy.isEmpty()) {
            return null;
        }

        return roleHierarchy.stream().collect(Collectors.joining("\n"));
    }
}
