package com.digitalsanctuary.spring.user.service;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.digitalsanctuary.spring.user.dto.UserDto;
import com.digitalsanctuary.spring.user.exceptions.UserAlreadyExistException;
import com.digitalsanctuary.spring.user.persistence.model.Role;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.repository.PasswordResetTokenRepository;
import com.digitalsanctuary.spring.user.persistence.repository.RoleRepository;
import com.digitalsanctuary.spring.user.persistence.repository.UserRepository;
import com.digitalsanctuary.spring.user.persistence.repository.VerificationTokenRepository;
import com.digitalsanctuary.spring.user.test.annotations.ServiceTest;
import com.digitalsanctuary.spring.user.test.builders.RoleTestDataBuilder;
import com.digitalsanctuary.spring.user.test.builders.UserTestDataBuilder;
import org.springframework.test.util.ReflectionTestUtils;

@ServiceTest
public class UserServiceTest {

    private static final String USER_ROLE_NAME = "ROLE_USER";
    @Mock
    private UserRepository userRepository;
    @Mock
    private VerificationTokenRepository tokenRepository;
    @Mock
    private PasswordResetTokenRepository passwordTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private SessionRegistry sessionRegistry;
    @Mock
    public UserEmailService userEmailService;
    @Mock
    public UserVerificationService userVerificationService;
    @Mock
    private DSUserDetailsService dsUserDetailsService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private AuthorityService authorityService;

    @InjectMocks
    private UserService userService;
    private User testUser;
    private UserDto testUserDto;

    @BeforeEach
    void setUp() {
        // Use test data builders for cleaner test data setup
        testUser = UserTestDataBuilder.aUser()
                .withEmail("test@example.com")
                .withFirstName("testFirstName")
                .withLastName("testLastName")
                .withPassword("testPassword")
                .withRole("ROLE_USER")
                .enabled()
                .build();

        testUserDto = new UserDto();
        testUserDto.setEmail("test@example.com");
        testUserDto.setFirstName("testFirstName");
        testUserDto.setLastName("testLastName");
        testUserDto.setPassword("testPassword");
        testUserDto.setRole(1);
    }

    @Test
    void registerNewUserAccount_returnsUserWhenUserIsNew() {
        // Given
        Role userRole = RoleTestDataBuilder.aUserRole().build();
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(roleRepository.findByName(USER_ROLE_NAME)).thenReturn(userRole);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // Set sendRegistrationVerificationEmail to true to test disabled user creation
        ReflectionTestUtils.setField(userService, "sendRegistrationVerificationEmail", true);
        
        // When
        User saved = userService.registerNewUserAccount(testUserDto);
        
        // Then
        assertThat(saved).isNotNull();
        assertThat(saved.getEmail()).isEqualTo(testUserDto.getEmail());
        assertThat(saved.getFirstName()).isEqualTo(testUserDto.getFirstName());
        assertThat(saved.getLastName()).isEqualTo(testUserDto.getLastName());
        assertThat(saved.isEnabled()).isFalse(); // New users should not be enabled until verified
        verify(userRepository).save(any(User.class));
    }

    @Test
    void registerNewUserAccount_throwsExceptionWhenUserExist() {
        // Given
        when(userRepository.findByEmail(testUser.getEmail())).thenReturn(testUser);
        
        // When & Then
        assertThatThrownBy(() -> userService.registerNewUserAccount(testUserDto))
                .isInstanceOf(UserAlreadyExistException.class)
                .hasMessageContaining("There is an account with that email address");
    }

    @Test
    void findByEmail_returnsUserWhenEmailExist() {
        // Given
        when(userRepository.findByEmail(testUser.getEmail())).thenReturn(testUser);
        
        // When
        User found = userService.findUserByEmail(testUser.getEmail());
        
        // Then
        assertThat(found).isEqualTo(testUser);
    }

    @Test
    void checkIfValidOldPassword_returnTrueIfValid() {
        // Given
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        
        // When
        boolean isValid = userService.checkIfValidOldPassword(testUser, testUser.getPassword());
        
        // Then
        assertThat(isValid).isTrue();
    }

    @Test
    void checkIfValidOldPassword_returnFalseIfInvalid() {
        // Given
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);
        
        // When
        boolean isValid = userService.checkIfValidOldPassword(testUser, "wrongPassword");
        
        // Then
        assertThat(isValid).isFalse();
    }

    @Test
    void changeUserPassword_encodesAndSavesNewPassword() {
        // Given
        String newPassword = "newTestPassword";
        String encodedPassword = "encodedNewPassword";
        when(passwordEncoder.encode(newPassword)).thenReturn(encodedPassword);
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // When
        userService.changeUserPassword(testUser, newPassword);

        // Then
        assertThat(testUser.getPassword()).isEqualTo(encodedPassword);
        verify(passwordEncoder).encode(newPassword);
        verify(userRepository).save(testUser);
    }

}
