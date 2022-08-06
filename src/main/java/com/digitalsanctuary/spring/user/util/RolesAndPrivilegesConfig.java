package com.digitalsanctuary.spring.user.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "user")
public class RolesAndPrivilegesConfig {
    private Map<String, List<String>> rolesAndPrivileges = new HashMap<String, List<String>>();

    private List<String> roleHierarchy = new ArrayList<String>();

    public String getRoleHierarchyString() {
        StringBuffer roleHierarchyStringBuf = new StringBuffer();
        if (roleHierarchy != null && !roleHierarchy.isEmpty()) {

            for (String roleRelationship : roleHierarchy) {
                roleHierarchyStringBuf.append(roleRelationship);
                roleHierarchyStringBuf.append("\n");
            }
        }
        // If we have built a list of hierarchy relationships, then return it.
        if (roleHierarchyStringBuf.length() > 0) {
            return roleHierarchyStringBuf.toString();
        } else {
            // otherwise, return null.
            return null;
        }

    }

}
