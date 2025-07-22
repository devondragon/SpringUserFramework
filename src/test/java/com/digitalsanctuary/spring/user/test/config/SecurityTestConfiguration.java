package com.digitalsanctuary.spring.user.test.config;

import com.digitalsanctuary.spring.user.persistence.model.Privilege;
import com.digitalsanctuary.spring.user.persistence.model.Role;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.service.DSUserDetails;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.TestingAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.context.support.WithSecurityContextFactory;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * Security test configuration providing test users and security setup for testing.
 * This configuration provides pre-configured users and roles for different test scenarios.
 */
@TestConfiguration
@Profile("test")
public class SecurityTestConfiguration {

    /**
     * Test authentication provider that accepts any authentication.
     */
    @Bean
    @Primary
    public AuthenticationProvider testAuthenticationProvider() {
        TestingAuthenticationProvider provider = new TestingAuthenticationProvider();
        return provider;
    }

    /**
     * In-memory user details service with pre-configured test users.
     */
    @Bean
    @Primary
    public UserDetailsService testUserDetailsService() {
        InMemoryUserDetailsManager manager = new InMemoryUserDetailsManager();
        
        // Create test users
        manager.createUser(createTestUser("user@test.com", "password", "ROLE_USER"));
        manager.createUser(createTestUser("admin@test.com", "password", "ROLE_ADMIN"));
        manager.createUser(createTestUser("moderator@test.com", "password", "ROLE_MODERATOR"));
        
        return manager;
    }

    /**
     * Creates a test user with DSUserDetails.
     */
    private DSUserDetails createTestUser(String email, String password, String... roles) {
        User user = new User();
        user.setId(email.hashCode() + 0L);
        user.setEmail(email);
        user.setPassword("{bcrypt}$2a$04$YDiv9c./ytEGZQopFfExoOgGlJL6/o0er0K.hiGb5TGKHUL8Ebn.."); // "password"
        user.setFirstName("Test");
        user.setLastName("User");
        user.setEnabled(true);
        
        List<Role> userRoles = new ArrayList<>();
        for (String roleName : roles) {
            Role role = new Role();
            role.setName(roleName);
            userRoles.add(role);
        }
        user.setRoles(userRoles);
        
        return new DSUserDetails(user);
    }

    /**
     * Test security context factory for creating custom security contexts.
     */
    @Bean
    public TestSecurityContextFactory testSecurityContextFactory() {
        return new TestSecurityContextFactory();
    }

    /**
     * Factory for creating test security contexts with custom users.
     */
    public static class TestSecurityContextFactory {
        
        /**
         * Creates a security context with a verified user.
         */
        public SecurityContext createVerifiedUserContext() {
            User user = createVerifiedUser();
            DSUserDetails userDetails = new DSUserDetails(user);
            UsernamePasswordAuthenticationToken auth = 
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            return context;
        }
        
        /**
         * Creates a security context with an admin user.
         */
        public SecurityContext createAdminContext() {
            User user = createAdminUser();
            DSUserDetails userDetails = new DSUserDetails(user);
            UsernamePasswordAuthenticationToken auth = 
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            return context;
        }
        
        /**
         * Creates a security context with an unverified user.
         */
        public SecurityContext createUnverifiedUserContext() {
            User user = createUnverifiedUser();
            DSUserDetails userDetails = new DSUserDetails(user);
            UsernamePasswordAuthenticationToken auth = 
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            return context;
        }
        
        private User createVerifiedUser() {
            User user = new User();
            user.setId(1L);
            user.setEmail("verified@test.com");
            user.setPassword("$2a$04$YDiv9c./ytEGZQopFfExoOgGlJL6/o0er0K.hiGb5TGKHUL8Ebn..");
            user.setFirstName("Verified");
            user.setLastName("User");
            user.setEnabled(true);
            
            Role userRole = new Role();
            userRole.setName("ROLE_USER");
            user.setRoles(Arrays.asList(userRole));
            
            return user;
        }
        
        private User createAdminUser() {
            User user = new User();
            user.setId(2L);
            user.setEmail("admin@test.com");
            user.setPassword("$2a$04$YDiv9c./ytEGZQopFfExoOgGlJL6/o0er0K.hiGb5TGKHUL8Ebn..");
            user.setFirstName("Admin");
            user.setLastName("User");
            user.setEnabled(true);
            
            Role adminRole = new Role();
            adminRole.setName("ROLE_ADMIN");
            
            // Add admin privileges
            List<Privilege> privileges = new ArrayList<>();
            Privilege readPrivilege = new Privilege();
            readPrivilege.setName("READ_PRIVILEGE");
            privileges.add(readPrivilege);
            
            Privilege writePrivilege = new Privilege();
            writePrivilege.setName("WRITE_PRIVILEGE");
            privileges.add(writePrivilege);
            
            adminRole.setPrivileges(new HashSet<>(privileges));
            user.setRoles(Arrays.asList(adminRole));
            
            return user;
        }
        
        private User createUnverifiedUser() {
            User user = new User();
            user.setId(3L);
            user.setEmail("unverified@test.com");
            user.setPassword("$2a$04$YDiv9c./ytEGZQopFfExoOgGlJL6/o0er0K.hiGb5TGKHUL8Ebn..");
            user.setFirstName("Unverified");
            user.setLastName("User");
            user.setEnabled(false); // Not verified
            
            Role userRole = new Role();
            userRole.setName("ROLE_USER");
            user.setRoles(Arrays.asList(userRole));
            
            return user;
        }
    }
}