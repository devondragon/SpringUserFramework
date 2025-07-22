package com.digitalsanctuary.spring.user.test.fixtures;

import com.digitalsanctuary.spring.user.dto.PasswordDto;
import com.digitalsanctuary.spring.user.dto.UserDto;
import com.digitalsanctuary.spring.user.persistence.model.PasswordResetToken;
import com.digitalsanctuary.spring.user.persistence.model.Role;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.model.VerificationToken;
import com.digitalsanctuary.spring.user.service.DSUserDetails;
import com.digitalsanctuary.spring.user.test.builders.RoleTestDataBuilder;
import com.digitalsanctuary.spring.user.test.builders.TokenTestDataBuilder;
import com.digitalsanctuary.spring.user.test.builders.UserTestDataBuilder;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.*;

/**
 * Centralized test fixtures utility providing common test data patterns.
 * This class promotes DRY principles and ensures consistent test data across the test suite.
 * 
 * Usage:
 * <pre>
 * User testUser = TestFixtures.Users.standardUser();
 * UserDto userDto = TestFixtures.DTOs.validUserRegistration();
 * OAuth2User oauth2User = TestFixtures.OAuth2.googleUser();
 * </pre>
 */
public final class TestFixtures {

    private TestFixtures() {
        // Utility class - prevent instantiation
    }

    /**
     * Common test constants used across multiple tests.
     */
    public static final class Constants {
        public static final String DEFAULT_TEST_EMAIL = "test@example.com";
        public static final String DEFAULT_TEST_PASSWORD = "TestPassword123!";
        public static final String DEFAULT_FIRST_NAME = "Test";
        public static final String DEFAULT_LAST_NAME = "User";
        public static final String ADMIN_EMAIL = "admin@test.com";
        public static final String LOCKED_USER_EMAIL = "locked@test.com";
        public static final String UNVERIFIED_USER_EMAIL = "unverified@test.com";
        public static final String OAUTH2_USER_EMAIL = "oauth2@test.com";
        public static final Long DEFAULT_USER_ID = 1L;
        public static final Long ADMIN_USER_ID = 2L;
        
        private Constants() {}
    }

    /**
     * Pre-configured User entity fixtures for common test scenarios.
     */
    public static final class Users {
        
        /**
         * Standard verified user with ROLE_USER.
         */
        public static User standardUser() {
            return UserTestDataBuilder.aUser()
                    .withId(Constants.DEFAULT_USER_ID)
                    .withEmail(Constants.DEFAULT_TEST_EMAIL)
                    .withFirstName(Constants.DEFAULT_FIRST_NAME)
                    .withLastName(Constants.DEFAULT_LAST_NAME)
                    .withPassword(Constants.DEFAULT_TEST_PASSWORD)
                    .withRole("ROLE_USER")
                    .verified()
                    .build();
        }

        /**
         * Admin user with ROLE_ADMIN and ROLE_USER.
         */
        public static User adminUser() {
            return UserTestDataBuilder.aUser()
                    .withId(Constants.ADMIN_USER_ID)
                    .withEmail(Constants.ADMIN_EMAIL)
                    .withFirstName("Admin")
                    .withLastName("User")
                    .withPassword(Constants.DEFAULT_TEST_PASSWORD)
                    .withRoles("ROLE_ADMIN", "ROLE_USER")
                    .verified()
                    .build();
        }

        /**
         * Unverified user (disabled) awaiting email verification.
         */
        public static User unverifiedUser() {
            return UserTestDataBuilder.aUser()
                    .withEmail(Constants.UNVERIFIED_USER_EMAIL)
                    .withFirstName("Unverified")
                    .withLastName("User")
                    .withPassword(Constants.DEFAULT_TEST_PASSWORD)
                    .withRole("ROLE_USER")
                    .unverified()
                    .build();
        }

        /**
         * Locked user due to failed login attempts.
         */
        public static User lockedUser() {
            return UserTestDataBuilder.aUser()
                    .withEmail(Constants.LOCKED_USER_EMAIL)
                    .withFirstName("Locked")
                    .withLastName("User")
                    .withPassword(Constants.DEFAULT_TEST_PASSWORD)
                    .withRole("ROLE_USER")
                    .locked()
                    .withFailedLoginAttempts(5)
                    .build();
        }

        /**
         * OAuth2 user from Google provider.
         */
        public static User oauth2GoogleUser() {
            return UserTestDataBuilder.aUser()
                    .withEmail(Constants.OAUTH2_USER_EMAIL)
                    .withFirstName("OAuth2")
                    .withLastName("User")
                    .fromOAuth2(User.Provider.GOOGLE)
                    .verified()
                    .withRole("ROLE_USER")
                    .build();
        }

        /**
         * Creates a user with custom email for unique test scenarios.
         */
        public static User withEmail(String email) {
            return UserTestDataBuilder.aUser()
                    .withEmail(email)
                    .withFirstName(Constants.DEFAULT_FIRST_NAME)
                    .withLastName(Constants.DEFAULT_LAST_NAME)
                    .withPassword(Constants.DEFAULT_TEST_PASSWORD)
                    .withRole("ROLE_USER")
                    .verified()
                    .build();
        }

        /**
         * Creates multiple test users with incremented emails.
         */
        public static List<User> multipleUsers(int count) {
            return UserTestDataBuilder.aUser()
                    .withEmail("user@test.com")
                    .withFirstName(Constants.DEFAULT_FIRST_NAME)
                    .withLastName(Constants.DEFAULT_LAST_NAME)
                    .withPassword(Constants.DEFAULT_TEST_PASSWORD)
                    .withRole("ROLE_USER")
                    .verified()
                    .buildMany(count);
        }

        private Users() {}
    }

    /**
     * DTO fixtures for API and form testing.
     */
    public static final class DTOs {
        
        /**
         * Valid user registration DTO.
         */
        public static UserDto validUserRegistration() {
            UserDto dto = new UserDto();
            dto.setEmail(Constants.DEFAULT_TEST_EMAIL);
            dto.setFirstName(Constants.DEFAULT_FIRST_NAME);
            dto.setLastName(Constants.DEFAULT_LAST_NAME);
            dto.setPassword(Constants.DEFAULT_TEST_PASSWORD);
            dto.setRole(1); // Assuming role ID 1 = ROLE_USER
            return dto;
        }

        /**
         * User registration DTO with missing email (invalid).
         */
        public static UserDto invalidUserRegistrationMissingEmail() {
            UserDto dto = validUserRegistration();
            dto.setEmail(null);
            return dto;
        }

        /**
         * User registration DTO with missing password (invalid).
         */
        public static UserDto invalidUserRegistrationMissingPassword() {
            UserDto dto = validUserRegistration();
            dto.setPassword(null);
            return dto;
        }

        /**
         * Valid password update DTO.
         */
        public static PasswordDto validPasswordUpdate() {
            PasswordDto dto = new PasswordDto();
            dto.setOldPassword(Constants.DEFAULT_TEST_PASSWORD);
            dto.setNewPassword("NewPassword123!");
            return dto;
        }

        /**
         * Invalid password update DTO with wrong old password.
         */
        public static PasswordDto invalidPasswordUpdateWrongOld() {
            PasswordDto dto = new PasswordDto();
            dto.setOldPassword("WrongPassword123!");
            dto.setNewPassword("NewPassword123!");
            return dto;
        }

        /**
         * User DTO for profile updates.
         */
        public static UserDto profileUpdate() {
            UserDto dto = new UserDto();
            dto.setFirstName("Updated");
            dto.setLastName("Name");
            return dto;
        }

        private DTOs() {}
    }

    /**
     * OAuth2 test fixtures for authentication testing.
     */
    public static final class OAuth2 {
        
        /**
         * Mock Google OAuth2 user.
         */
        public static OAuth2User googleUser() {
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("sub", "google-123456");
            attributes.put("name", "Google Test User");
            attributes.put("email", Constants.OAUTH2_USER_EMAIL);
            attributes.put("email_verified", true);
            attributes.put("picture", "https://example.com/picture.jpg");
            
            return new DefaultOAuth2User(
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")),
                attributes,
                "sub"
            );
        }

        /**
         * Mock GitHub OAuth2 user.
         */
        public static OAuth2User githubUser() {
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("id", "github-789012");
            attributes.put("login", "githubuser");
            attributes.put("name", "GitHub Test User");
            attributes.put("email", "github.user@test.com");
            attributes.put("avatar_url", "https://example.com/avatar.jpg");
            
            return new DefaultOAuth2User(
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")),
                attributes,
                "id"
            );
        }

        /**
         * Generic OAuth2 user with custom attributes.
         */
        public static OAuth2User customUser(String email, String name, String providerId) {
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("sub", providerId);
            attributes.put("name", name);
            attributes.put("email", email);
            attributes.put("email_verified", true);
            
            return new DefaultOAuth2User(
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")),
                attributes,
                "sub"
            );
        }

        private OAuth2() {}
    }

    /**
     * Security-related test fixtures.
     */
    public static final class Security {
        
        /**
         * Standard DSUserDetails for the default test user.
         */
        public static DSUserDetails standardUserDetails() {
            return new DSUserDetails(Users.standardUser());
        }

        /**
         * Admin DSUserDetails.
         */
        public static DSUserDetails adminUserDetails() {
            return new DSUserDetails(Users.adminUser());
        }

        /**
         * DSUserDetails for a custom user.
         */
        public static DSUserDetails userDetailsFor(User user) {
            return new DSUserDetails(user);
        }

        private Security() {}
    }

    /**
     * Token fixtures for verification and password reset testing.
     */
    public static final class Tokens {
        
        /**
         * Valid verification token for the standard user.
         */
        public static VerificationToken validVerificationToken() {
            return TokenTestDataBuilder.aVerificationToken()
                    .forUser(Users.standardUser())
                    .withToken("valid-verification-token-123")
                    .expiringInHours(24)
                    .build();
        }

        /**
         * Expired verification token.
         */
        public static VerificationToken expiredVerificationToken() {
            return TokenTestDataBuilder.anExpiredVerificationToken()
                    .forUser(Users.standardUser())
                    .withToken("expired-verification-token-123")
                    .expiredHoursAgo(1)
                    .build();
        }

        /**
         * Valid password reset token for the standard user.
         */
        public static PasswordResetToken validPasswordResetToken() {
            return TokenTestDataBuilder.aPasswordResetToken()
                    .forUser(Users.standardUser())
                    .withToken("valid-password-reset-token-123")
                    .expiringInHours(1)
                    .build();
        }

        /**
         * Expired password reset token.
         */
        public static PasswordResetToken expiredPasswordResetToken() {
            return TokenTestDataBuilder.anExpiredPasswordResetToken()
                    .forUser(Users.standardUser())
                    .withToken("expired-password-reset-token-123")
                    .expiredMinutesAgo(120)
                    .build();
        }

        private Tokens() {}
    }

    /**
     * Role fixtures for authorization testing.
     */
    public static final class Roles {
        
        /**
         * Standard user role.
         */
        public static Role userRole() {
            return RoleTestDataBuilder.aUserRole().build();
        }

        /**
         * Admin role with additional privileges.
         */
        public static Role adminRole() {
            return RoleTestDataBuilder.anAdminRole().build();
        }

        /**
         * Role with custom name and privileges.
         */
        public static Role customRole(String name, String... privileges) {
            RoleTestDataBuilder builder = RoleTestDataBuilder.aRole().withName(name);
            for (String privilege : privileges) {
                builder.withPrivilege(privilege);
            }
            return builder.build();
        }

        private Roles() {}
    }

    /**
     * Application URLs and endpoints commonly used in tests.
     */
    public static final class URLs {
        public static final String APP_URL = "https://test.example.com";
        public static final String REGISTRATION_ENDPOINT = "/user/registration";
        public static final String LOGIN_ENDPOINT = "/user/login";
        public static final String PASSWORD_RESET_ENDPOINT = "/user/resetPassword";
        public static final String UPDATE_PASSWORD_ENDPOINT = "/user/updatePassword";
        public static final String UPDATE_USER_ENDPOINT = "/user/updateUser";
        public static final String DELETE_ACCOUNT_ENDPOINT = "/user/deleteAccount";
        
        private URLs() {}
    }

    /**
     * Common test scenarios and workflows.
     */
    public static final class Scenarios {
        
        /**
         * Complete user registration scenario data.
         */
        public static class UserRegistration {
            private final User newUser;
            private final UserDto registrationDto;
            private final VerificationToken verificationToken;
            
            public UserRegistration() {
                this.registrationDto = DTOs.validUserRegistration();
                this.newUser = Users.withEmail(registrationDto.getEmail());
                this.verificationToken = Tokens.validVerificationToken();
            }
            
            public User getNewUser() { return newUser; }
            public UserDto getRegistrationDto() { return registrationDto; }
            public VerificationToken getVerificationToken() { return verificationToken; }
        }

        /**
         * Password reset workflow data.
         */
        public static class PasswordReset {
            private final User user;
            private final PasswordResetToken resetToken;
            private final PasswordDto passwordDto;
            
            public PasswordReset() {
                this.user = Users.standardUser();
                this.resetToken = Tokens.validPasswordResetToken();
                this.passwordDto = DTOs.validPasswordUpdate();
            }
            
            public User getUser() { return user; }
            public PasswordResetToken getResetToken() { return resetToken; }
            public PasswordDto getPasswordDto() { return passwordDto; }
        }

        private Scenarios() {}
    }
}