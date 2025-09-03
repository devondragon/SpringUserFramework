package com.digitalsanctuary.spring.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import com.digitalsanctuary.spring.user.persistence.model.Privilege;
import com.digitalsanctuary.spring.user.persistence.model.Role;
import com.digitalsanctuary.spring.user.persistence.model.User;

/**
 * Comprehensive unit tests for LoginHelperService that verify actual business logic for user authentication helper operations including last activity
 * tracking, account unlocking, and authority assignment.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LoginHelperService Tests")
class LoginHelperServiceTest {

    @Mock
    private LoginAttemptService loginAttemptService;

    @Mock
    private AuthorityService authorityService;

    @InjectMocks
    private LoginHelperService loginHelperService;

    private User testUser;
    private Collection<? extends GrantedAuthority> testAuthorities;

    @BeforeEach
    void setUp() {
        // Create a test user
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("test@example.com");
        testUser.setFirstName("Test");
        testUser.setLastName("User");
        testUser.setPassword("encodedPassword");
        testUser.setEnabled(true);
        testUser.setLocked(false);

        // Create test roles and privileges
        Role userRole = new Role();
        userRole.setId(1L);
        userRole.setName("ROLE_USER");

        Privilege readPrivilege = new Privilege();
        readPrivilege.setId(1L);
        readPrivilege.setName("READ_PRIVILEGE");

        Privilege writePrivilege = new Privilege();
        writePrivilege.setId(2L);
        writePrivilege.setName("WRITE_PRIVILEGE");

        Set<Privilege> privileges = new HashSet<>();
        privileges.add(readPrivilege);
        privileges.add(writePrivilege);
        userRole.setPrivileges(privileges);
        testUser.setRoles(Collections.singletonList(userRole));

        // Create test authorities
        Set<GrantedAuthority> authorities = new HashSet<>();
        authorities.add(new SimpleGrantedAuthority("READ_PRIVILEGE"));
        authorities.add(new SimpleGrantedAuthority("WRITE_PRIVILEGE"));
        testAuthorities = authorities;
    }

    @Nested
    @DisplayName("User Login Helper Tests")
    class UserLoginHelperTests {

        @Test
        @DisplayName("Should update last activity date on login")
        void shouldUpdateLastActivityDate() {
            // Given
            Date beforeLogin = testUser.getLastActivityDate();
            when(loginAttemptService.checkIfUserShouldBeUnlocked(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(authorityService.getAuthoritiesFromUser(testUser)).thenReturn((Collection) testAuthorities);

            // When
            DSUserDetails result = loginHelperService.userLoginHelper(testUser);

            // Then
            assertThat(testUser.getLastActivityDate()).isNotNull();
            assertThat(testUser.getLastActivityDate())
                    .isAfterOrEqualTo(beforeLogin == null ? new Date(System.currentTimeMillis() - 1000) : beforeLogin);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should check if user should be unlocked during login")
        void shouldCheckUserUnlockStatus() {
            // Given
            testUser.setLocked(true);
            testUser.setLockedDate(new Date(System.currentTimeMillis() - 3600000)); // 1 hour ago

            User unlockedUser = new User();
            unlockedUser.setId(testUser.getId());
            unlockedUser.setEmail(testUser.getEmail());
            unlockedUser.setLocked(false);
            unlockedUser.setLockedDate(null);

            when(loginAttemptService.checkIfUserShouldBeUnlocked(testUser)).thenReturn(unlockedUser);
            when(authorityService.getAuthoritiesFromUser(unlockedUser)).thenReturn((Collection) testAuthorities);

            // When
            DSUserDetails result = loginHelperService.userLoginHelper(testUser);

            // Then
            verify(loginAttemptService).checkIfUserShouldBeUnlocked(testUser);
            assertThat(result.getUser()).isEqualTo(unlockedUser);
            assertThat(result.isAccountNonLocked()).isTrue();
        }

        @Test
        @DisplayName("Should create DSUserDetails with correct authorities")
        void shouldCreateUserDetailsWithAuthorities() {
            // Given
            when(loginAttemptService.checkIfUserShouldBeUnlocked(testUser)).thenReturn(testUser);
            when(authorityService.getAuthoritiesFromUser(testUser)).thenReturn((Collection) testAuthorities);

            // When
            DSUserDetails result = loginHelperService.userLoginHelper(testUser);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getUser()).isEqualTo(testUser);
            assertThat(result.getAuthorities()).hasSize(2);
            assertThat(result.getAuthorities()).isEqualTo(testAuthorities);
            assertThat(result.getUsername()).isEqualTo("test@example.com");
            assertThat(result.getPassword()).isEqualTo("encodedPassword");
        }

        @Test
        @DisplayName("Should handle user with no authorities")
        void shouldHandleUserWithNoAuthorities() {
            // Given
            when(loginAttemptService.checkIfUserShouldBeUnlocked(testUser)).thenReturn(testUser);
            when(authorityService.getAuthoritiesFromUser(testUser)).thenReturn(Collections.emptyList());

            // When
            DSUserDetails result = loginHelperService.userLoginHelper(testUser);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getAuthorities()).isEmpty();
            assertThat(result.getUser()).isEqualTo(testUser);
        }

        @Test
        @DisplayName("Should handle locked user that remains locked")
        void shouldHandleLockedUserThatRemainsLocked() {
            // Given
            testUser.setLocked(true);
            testUser.setFailedLoginAttempts(5);
            Date lockedDate = new Date(System.currentTimeMillis() - 60000); // 1 minute ago
            testUser.setLockedDate(lockedDate);

            when(loginAttemptService.checkIfUserShouldBeUnlocked(testUser)).thenReturn(testUser); // User remains locked
            when(authorityService.getAuthoritiesFromUser(testUser)).thenReturn((Collection) testAuthorities);

            // When
            DSUserDetails result = loginHelperService.userLoginHelper(testUser);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getUser().isLocked()).isTrue();
            assertThat(result.isAccountNonLocked()).isFalse();
            assertThat(result.getUser().getFailedLoginAttempts()).isEqualTo(5);
        }

        @Test
        @DisplayName("Should handle disabled user correctly")
        void shouldHandleDisabledUser() {
            // Given
            testUser.setEnabled(false);
            when(loginAttemptService.checkIfUserShouldBeUnlocked(testUser)).thenReturn(testUser);
            when(authorityService.getAuthoritiesFromUser(testUser)).thenReturn((Collection) testAuthorities);

            // When
            DSUserDetails result = loginHelperService.userLoginHelper(testUser);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.isEnabled()).isFalse();
            assertThat(result.getUser().isEnabled()).isFalse();
            // Even disabled users should get their authorities
            assertThat(result.getAuthorities()).isNotEmpty();
        }

        @Test
        @DisplayName("Should preserve user state during login helper process")
        void shouldPreserveUserStateDuringProcess() {
            // Given
            testUser.setProvider(User.Provider.GOOGLE);
            // Note: imageUrl and usingMfa fields don't exist in User class

            when(loginAttemptService.checkIfUserShouldBeUnlocked(testUser)).thenReturn(testUser);
            when(authorityService.getAuthoritiesFromUser(testUser)).thenReturn((Collection) testAuthorities);

            // When
            DSUserDetails result = loginHelperService.userLoginHelper(testUser);

            // Then
            User resultUser = result.getUser();
            assertThat(resultUser.getProvider()).isEqualTo(User.Provider.GOOGLE);
            assertThat(resultUser.getEmail()).isEqualTo("test@example.com");
            assertThat(resultUser.getFirstName()).isEqualTo("Test");
            assertThat(resultUser.getLastName()).isEqualTo("User");
        }
    }

    @Nested
    @DisplayName("Integration with LoginAttemptService Tests")
    class LoginAttemptIntegrationTests {

        @Test
        @DisplayName("Should automatically unlock user after lockout duration")
        void shouldAutomaticallyUnlockUserAfterDuration() {
            // Given - user was locked 31 minutes ago (assuming 30 min lockout duration)
            testUser.setLocked(true);
            testUser.setFailedLoginAttempts(5);
            testUser.setLockedDate(new Date(System.currentTimeMillis() - 1860000)); // 31 minutes ago

            // Simulate unlock behavior
            User unlockedUser = new User();
            unlockedUser.setId(testUser.getId());
            unlockedUser.setEmail(testUser.getEmail());
            unlockedUser.setLocked(false);
            unlockedUser.setFailedLoginAttempts(0);
            unlockedUser.setLockedDate(null);
            unlockedUser.setEnabled(true);

            when(loginAttemptService.checkIfUserShouldBeUnlocked(testUser)).thenReturn(unlockedUser);
            when(authorityService.getAuthoritiesFromUser(unlockedUser)).thenReturn((Collection) testAuthorities);

            // When
            DSUserDetails result = loginHelperService.userLoginHelper(testUser);

            // Then
            assertThat(result.isAccountNonLocked()).isTrue();
            assertThat(result.getUser().isLocked()).isFalse();
            assertThat(result.getUser().getFailedLoginAttempts()).isEqualTo(0);
            assertThat(result.getUser().getLockedDate()).isNull();
        }

        @Test
        @DisplayName("Should track timing of last activity update")
        void shouldTrackTimingOfLastActivityUpdate() {
            // Given
            Date testStartTime = new Date();
            when(loginAttemptService.checkIfUserShouldBeUnlocked(testUser)).thenReturn(testUser);
            when(authorityService.getAuthoritiesFromUser(testUser)).thenReturn((Collection) testAuthorities);

            // When
            DSUserDetails result = loginHelperService.userLoginHelper(testUser);
            Date testEndTime = new Date();

            // Then
            assertThat(result.getUser().getLastActivityDate()).isNotNull();
            assertThat(result.getUser().getLastActivityDate()).isAfterOrEqualTo(testStartTime).isBeforeOrEqualTo(testEndTime);
        }
    }

    @Nested
    @DisplayName("Authority Assignment Tests")
    class AuthorityAssignmentTests {

        @Test
        @DisplayName("Should assign multiple roles and privileges correctly")
        void shouldAssignMultipleRolesAndPrivileges() {
            // Given - User with multiple roles
            Role adminRole = new Role();
            adminRole.setId(2L);
            adminRole.setName("ROLE_ADMIN");

            Privilege adminPrivilege = new Privilege();
            adminPrivilege.setId(3L);
            adminPrivilege.setName("ADMIN_PRIVILEGE");

            Set<Privilege> adminPrivileges = new HashSet<>();
            adminPrivileges.add(adminPrivilege);
            adminRole.setPrivileges(adminPrivileges);
            testUser.getRoles().add(adminRole);

            Set<GrantedAuthority> multipleAuthorities = new HashSet<>();
            multipleAuthorities.add(new SimpleGrantedAuthority("READ_PRIVILEGE"));
            multipleAuthorities.add(new SimpleGrantedAuthority("WRITE_PRIVILEGE"));
            multipleAuthorities.add(new SimpleGrantedAuthority("ADMIN_PRIVILEGE"));

            when(loginAttemptService.checkIfUserShouldBeUnlocked(testUser)).thenReturn(testUser);
            when(authorityService.getAuthoritiesFromUser(testUser)).thenReturn((Collection) multipleAuthorities);

            // When
            DSUserDetails result = loginHelperService.userLoginHelper(testUser);

            // Then
            assertThat(result.getAuthorities()).hasSize(3);
            assertThat(result.getAuthorities()).isEqualTo(multipleAuthorities);
        }

        @Test
        @DisplayName("Should handle user with complex role hierarchy")
        void shouldHandleComplexRoleHierarchy() {
            // Given
            Set<GrantedAuthority> complexAuthorities = new HashSet<>();
            complexAuthorities.add(new SimpleGrantedAuthority("ROLE_USER"));
            complexAuthorities.add(new SimpleGrantedAuthority("ROLE_MODERATOR"));
            complexAuthorities.add(new SimpleGrantedAuthority("READ_PRIVILEGE"));
            complexAuthorities.add(new SimpleGrantedAuthority("WRITE_PRIVILEGE"));
            complexAuthorities.add(new SimpleGrantedAuthority("DELETE_PRIVILEGE"));

            when(loginAttemptService.checkIfUserShouldBeUnlocked(testUser)).thenReturn(testUser);
            when(authorityService.getAuthoritiesFromUser(testUser)).thenReturn((Collection) complexAuthorities);

            // When
            DSUserDetails result = loginHelperService.userLoginHelper(testUser);

            // Then
            assertThat(result.getAuthorities()).hasSize(5);
            boolean hasUserRole = result.getAuthorities().stream().anyMatch(auth -> auth.getAuthority().equals("ROLE_USER"));
            boolean hasModeratorRole = result.getAuthorities().stream().anyMatch(auth -> auth.getAuthority().equals("ROLE_MODERATOR"));
            assertThat(hasUserRole).isTrue();
            assertThat(hasModeratorRole).isTrue();
        }
    }

    @Nested
    @DisplayName("DSUserDetails Creation Tests")
    class DSUserDetailsCreationTests {

        @Test
        @DisplayName("Should create valid DSUserDetails with all user information")
        void shouldCreateCompleteUserDetails() {
            // Given
            // User class doesn't have setFullName or setImageUrl methods
            testUser.setFirstName("Test");
            testUser.setLastName("User");

            when(loginAttemptService.checkIfUserShouldBeUnlocked(testUser)).thenReturn(testUser);
            when(authorityService.getAuthoritiesFromUser(testUser)).thenReturn((Collection) testAuthorities);

            // When
            DSUserDetails result = loginHelperService.userLoginHelper(testUser);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getUsername()).isEqualTo("test@example.com");
            assertThat(result.getPassword()).isEqualTo("encodedPassword");
            assertThat(result.getName()).isEqualTo("Test User"); // getFullName() computes from first+last
            assertThat(result.isAccountNonExpired()).isTrue();
            assertThat(result.isCredentialsNonExpired()).isTrue();
            assertThat(result.isEnabled()).isTrue();
            assertThat(result.isAccountNonLocked()).isTrue();
        }

        @Test
        @DisplayName("Should handle OAuth2 user correctly")
        void shouldHandleOAuth2User() {
            // Given
            testUser.setProvider(User.Provider.GOOGLE);
            testUser.setPassword(null); // OAuth2 users don't have passwords

            when(loginAttemptService.checkIfUserShouldBeUnlocked(testUser)).thenReturn(testUser);
            when(authorityService.getAuthoritiesFromUser(testUser)).thenReturn((Collection) testAuthorities);

            // When
            DSUserDetails result = loginHelperService.userLoginHelper(testUser);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getPassword()).isNull();
            assertThat(result.getUser().getProvider()).isEqualTo(User.Provider.GOOGLE);
            assertThat(result.getUser().getEmail()).isEqualTo("test@example.com");
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle user with null last activity date")
        void shouldHandleNullLastActivityDate() {
            // Given
            testUser.setLastActivityDate(null);

            when(loginAttemptService.checkIfUserShouldBeUnlocked(testUser)).thenReturn(testUser);
            when(authorityService.getAuthoritiesFromUser(testUser)).thenReturn((Collection) testAuthorities);

            // When
            DSUserDetails result = loginHelperService.userLoginHelper(testUser);

            // Then
            assertThat(result).isNotNull();
            assertThat(testUser.getLastActivityDate()).isNotNull();
        }

        @Test
        @DisplayName("Should handle rapid successive logins correctly")
        void shouldHandleRapidSuccessiveLogins() {
            // Given
            when(loginAttemptService.checkIfUserShouldBeUnlocked(testUser)).thenReturn(testUser);
            when(authorityService.getAuthoritiesFromUser(testUser)).thenReturn((Collection) testAuthorities);

            // When - Simulate rapid successive logins
            DSUserDetails result1 = loginHelperService.userLoginHelper(testUser);
            Date firstLoginTime = testUser.getLastActivityDate();

            // Small delay to ensure different timestamps
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                // Ignore
            }

            DSUserDetails result2 = loginHelperService.userLoginHelper(testUser);
            Date secondLoginTime = testUser.getLastActivityDate();

            // Then
            assertThat(result1).isNotNull();
            assertThat(result2).isNotNull();
            assertThat(secondLoginTime).isAfter(firstLoginTime);
        }
    }
}
