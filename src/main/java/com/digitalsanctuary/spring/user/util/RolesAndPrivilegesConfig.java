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
package com.digitalsanctuary.spring.user.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "user")
public class RolesAndPrivilegesConfig {
    private Map<String, List<String>> rolesAndPrivileges = new HashMap<>();

    private List<String> roleHierarchy = new ArrayList<>();

    /**
     * Returns a formatted string representation of the role hierarchy, where each role relationship is separated by a newline character. Returns
     * {@code null} if the role hierarchy is empty or {@code null}.
     *
     * @return a formatted string representation of the role hierarchy, or {@code null} if the hierarchy is empty or {@code null}
     */
    public String getRoleHierarchyString() {
        if (roleHierarchy == null || roleHierarchy.isEmpty()) {
            return null;
        }

        return roleHierarchy.stream().collect(Collectors.joining("\n"));
    }
}
