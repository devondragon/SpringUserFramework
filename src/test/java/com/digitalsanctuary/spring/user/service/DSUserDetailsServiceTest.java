package com.digitalsanctuary.spring.user.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.digitalsanctuary.spring.user.persistence.model.Role;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.repository.UserRepository;
import com.digitalsanctuary.spring.user.test.annotations.ServiceTest;
import com.digitalsanctuary.spring.user.test.builders.RoleTestDataBuilder;
import com.digitalsanctuary.spring.user.test.builders.UserTestDataBuilder;

/**
 * Unit tests for DSUserDetailsService.
 * 
 * This test class verifies the authentication service behavior including:
 * - Loading users by email
 * - Handling non-existent users
 * - Proper delegation to LoginHelperService
 * - DSUserDetails object creation
 */
@ServiceTest
@DisplayName("DSUserDetailsService Tests")
class DSUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private LoginHelperService loginHelperService;

    @InjectMocks
    private DSUserDetailsService dsUserDetailsService;

    private User testUser;
    private DSUserDetails mockUserDetails;

    @BeforeEach
    void setUp() {
        // Create a test user with a role
        Role userRole = RoleTestDataBuilder.aRole()
                .withName("ROLE_USER")
                .build();
        
        testUser = UserTestDataBuilder.aVerifiedUser()
                .withEmail("test@example.com")
                .withPassword("encodedPassword123")
                .withFirstName("Test")
                .withLastName("User")
                .withRoles(Arrays.asList(userRole))
                .build();

        // Create mock DSUserDetails that LoginHelperService would return
        mockUserDetails = new DSUserDetails(testUser, 
                Arrays.asList(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @Test
    @DisplayName("Should load user by email successfully")
    void loadUserByUsername_withValidEmail_returnsUserDetails() {
        // Given
        String email = "test@example.com";
        when(userRepository.findByEmail(email)).thenReturn(testUser);
        when(loginHelperService.userLoginHelper(testUser)).thenReturn(mockUserDetails);

        // When
        DSUserDetails result = dsUserDetailsService.loadUserByUsername(email);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo(email);
        assertThat(result.getUser()).isEqualTo(testUser);
        assertThat(result.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");

        // Verify interactions
        verify(userRepository).findByEmail(email);
        verify(loginHelperService).userLoginHelper(testUser);
    }

    @Test
    @DisplayName("Should throw exception when user not found")
    void loadUserByUsername_withNonExistentEmail_throwsException() {
        // Given
        String email = "nonexistent@example.com";
        when(userRepository.findByEmail(email)).thenReturn(null);

        // When & Then
        assertThatThrownBy(() -> dsUserDetailsService.loadUserByUsername(email))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("No user found with email/username: " + email);

        // Verify LoginHelperService was not called
        verify(loginHelperService, never()).userLoginHelper(any());
    }

    @Test
    @DisplayName("Should handle null email")
    void loadUserByUsername_withNullEmail_throwsException() {
        // Given
        when(userRepository.findByEmail(null)).thenReturn(null);

        // When & Then
        assertThatThrownBy(() -> dsUserDetailsService.loadUserByUsername(null))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("No user found with email/username: null");

        verify(userRepository).findByEmail(null);
        verify(loginHelperService, never()).userLoginHelper(any());
    }

    @Test
    @DisplayName("Should handle empty email")
    void loadUserByUsername_withEmptyEmail_throwsException() {
        // Given
        String emptyEmail = "";
        when(userRepository.findByEmail(emptyEmail)).thenReturn(null);

        // When & Then
        assertThatThrownBy(() -> dsUserDetailsService.loadUserByUsername(emptyEmail))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("No user found with email/username: ");

        verify(userRepository).findByEmail(emptyEmail);
        verify(loginHelperService, never()).userLoginHelper(any());
    }

    @Test
    @DisplayName("Should load disabled user successfully")
    void loadUserByUsername_withDisabledUser_returnsUserDetails() {
        // Given
        User disabledUser = UserTestDataBuilder.anUnverifiedUser()
                .withEmail("disabled@example.com")
                .build();
        DSUserDetails disabledUserDetails = new DSUserDetails(disabledUser, Collections.emptyList());
        
        when(userRepository.findByEmail("disabled@example.com")).thenReturn(disabledUser);
        when(loginHelperService.userLoginHelper(disabledUser)).thenReturn(disabledUserDetails);

        // When
        DSUserDetails result = dsUserDetailsService.loadUserByUsername("disabled@example.com");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.isEnabled()).isFalse();
        assertThat(result.getUsername()).isEqualTo("disabled@example.com");
    }

    @Test
    @DisplayName("Should load locked user successfully")
    void loadUserByUsername_withLockedUser_returnsUserDetails() {
        // Given
        User lockedUser = UserTestDataBuilder.aLockedUser()
                .withEmail("locked@example.com")
                .build();
        DSUserDetails lockedUserDetails = new DSUserDetails(lockedUser, 
                Arrays.asList(new SimpleGrantedAuthority("ROLE_USER")));
        
        when(userRepository.findByEmail("locked@example.com")).thenReturn(lockedUser);
        when(loginHelperService.userLoginHelper(lockedUser)).thenReturn(lockedUserDetails);

        // When
        DSUserDetails result = dsUserDetailsService.loadUserByUsername("locked@example.com");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.isAccountNonLocked()).isFalse();
        assertThat(result.getUsername()).isEqualTo("locked@example.com");
    }

    @Test
    @DisplayName("Should load user with multiple roles")
    void loadUserByUsername_withMultipleRoles_returnsUserDetailsWithAllAuthorities() {
        // Given
        Role userRole = RoleTestDataBuilder.aRole().withName("ROLE_USER").build();
        Role adminRole = RoleTestDataBuilder.aRole().withName("ROLE_ADMIN").build();
        
        User multiRoleUser = UserTestDataBuilder.aVerifiedUser()
                .withEmail("multirole@example.com")
                .withRoles(Arrays.asList(userRole, adminRole))
                .build();
        
        DSUserDetails multiRoleUserDetails = new DSUserDetails(multiRoleUser, 
                Arrays.asList(
                    new SimpleGrantedAuthority("ROLE_USER"),
                    new SimpleGrantedAuthority("ROLE_ADMIN")
                ));
        
        when(userRepository.findByEmail("multirole@example.com")).thenReturn(multiRoleUser);
        when(loginHelperService.userLoginHelper(multiRoleUser)).thenReturn(multiRoleUserDetails);

        // When
        DSUserDetails result = dsUserDetailsService.loadUserByUsername("multirole@example.com");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    @DisplayName("Should handle email with special characters")
    void loadUserByUsername_withSpecialCharactersInEmail_returnsUserDetails() {
        // Given
        String specialEmail = "test+tag@sub.example.com";
        User userWithSpecialEmail = UserTestDataBuilder.aVerifiedUser()
                .withEmail(specialEmail)
                .build();
        DSUserDetails specialEmailUserDetails = new DSUserDetails(userWithSpecialEmail, 
                Arrays.asList(new SimpleGrantedAuthority("ROLE_USER")));
        
        when(userRepository.findByEmail(specialEmail)).thenReturn(userWithSpecialEmail);
        when(loginHelperService.userLoginHelper(userWithSpecialEmail)).thenReturn(specialEmailUserDetails);

        // When
        DSUserDetails result = dsUserDetailsService.loadUserByUsername(specialEmail);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo(specialEmail);
    }

    @Test
    @DisplayName("Should verify DSUserDetails properties are correctly mapped")
    void loadUserByUsername_verifiesUserDetailsPropertiesMapping() {
        // Given
        String email = "mapping@example.com";
        String plainPassword = "password123";
        
        User user = UserTestDataBuilder.aVerifiedUser()
                .withEmail(email)
                .withPassword(plainPassword) // Will be encoded by builder
                .withFirstName("John")
                .withLastName("Doe")
                .build();
        
        DSUserDetails userDetails = new DSUserDetails(user, 
                Arrays.asList(new SimpleGrantedAuthority("ROLE_USER")));
        
        when(userRepository.findByEmail(email)).thenReturn(user);
        when(loginHelperService.userLoginHelper(user)).thenReturn(userDetails);

        // When
        DSUserDetails result = dsUserDetailsService.loadUserByUsername(email);

        // Then
        assertThat(result.getUsername()).isEqualTo(email);
        assertThat(result.getPassword()).isNotNull();
        assertThat(result.getPassword()).startsWith("$2a$"); // BCrypt encoded
        assertThat(result.isEnabled()).isTrue();
        assertThat(result.isAccountNonExpired()).isTrue();
        assertThat(result.isAccountNonLocked()).isTrue();
        assertThat(result.isCredentialsNonExpired()).isTrue();
        assertThat(result.getName()).isEqualTo("John Doe");
        assertThat(result.getUser()).isEqualTo(user);
    }

    @Test
    @DisplayName("Should pass correct user to LoginHelperService")
    void loadUserByUsername_verifiesCorrectUserPassedToLoginHelper() {
        // Given
        String email = "verify@example.com";
        User user = UserTestDataBuilder.aVerifiedUser()
                .withEmail(email)
                .build();
        
        when(userRepository.findByEmail(email)).thenReturn(user);
        when(loginHelperService.userLoginHelper(any())).thenReturn(mockUserDetails);

        // When
        dsUserDetailsService.loadUserByUsername(email);

        // Then
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(loginHelperService).userLoginHelper(userCaptor.capture());
        
        User capturedUser = userCaptor.getValue();
        assertThat(capturedUser).isEqualTo(user);
        assertThat(capturedUser.getEmail()).isEqualTo(email);
    }
}