package com.digitalsanctuary.spring.user.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

import com.digitalsanctuary.spring.user.audit.AuditEvent;
import com.digitalsanctuary.spring.user.dto.PasswordDto;
import com.digitalsanctuary.spring.user.dto.UserDto;
import com.digitalsanctuary.spring.user.dto.UserProfileUpdateDto;
import com.digitalsanctuary.spring.user.event.OnRegistrationCompleteEvent;
import com.digitalsanctuary.spring.user.exceptions.InvalidOldPasswordException;
import com.digitalsanctuary.spring.user.exceptions.UserAlreadyExistException;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.service.DSUserDetails;
import com.digitalsanctuary.spring.user.service.PasswordPolicyService;
import com.digitalsanctuary.spring.user.service.UserEmailService;
import com.digitalsanctuary.spring.user.service.UserService;
import com.digitalsanctuary.spring.user.test.builders.UserTestDataBuilder;
import com.digitalsanctuary.spring.user.util.JSONResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.HttpStatus;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.Collections;
import java.util.Locale;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserAPI Unit Tests")
public class UserAPIUnitTest {

    private MockMvc mockMvc;
    
    /**
     * Test exception handler to properly handle SecurityException in unit tests
     */
    @RestControllerAdvice
    static class TestExceptionHandler {
        @ExceptionHandler(SecurityException.class)
        public JSONResponse handleSecurityException(SecurityException e) {
            return JSONResponse.builder()
                    .success(false)
                    .code(401)
                    .message(e.getMessage())
                    .build();
        }
    }
    
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private UserService userService;

    @Mock
    private UserEmailService userEmailService;

    @Mock
    private MessageSource messageSource;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private PasswordPolicyService passwordPolicyService;
    
    @InjectMocks
    private UserAPI userAPI;

    private User testUser;
    private UserDto testUserDto;
    private DSUserDetails testUserDetails;

    @BeforeEach
    void setUp() {
        testUser = UserTestDataBuilder.aUser()
                .withId(1L)
                .withEmail("test@example.com")
                .withFirstName("Test")
                .withLastName("User")
                .withPassword("encodedPassword")
                .enabled()
                .build();

        testUserDto = new UserDto();
        testUserDto.setEmail("test@example.com");
        testUserDto.setFirstName("Test");
        testUserDto.setLastName("User");
        testUserDto.setPassword("password123");
        testUserDto.setMatchingPassword("password123");
        testUserDto.setRole(1);

        testUserDetails = new DSUserDetails(testUser);
        
        // Set field values using reflection
        ReflectionTestUtils.setField(userAPI, "registrationPendingURI", "/user/registration-pending.html");
        ReflectionTestUtils.setField(userAPI, "registrationSuccessURI", "/user/registration-complete.html");
        ReflectionTestUtils.setField(userAPI, "forgotPasswordPendingURI", "/user/forgot-password-pending.html");
        
        // Build MockMvc with standalone setup, custom argument resolver, and exception handler
        mockMvc = MockMvcBuilders.standaloneSetup(userAPI)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new TestExceptionHandler())
                .build();
    }

    @Nested
    @DisplayName("User Registration Tests")
    class UserRegistrationTests {

        @Test
        @DisplayName("POST /user/registration - successful registration with verification email")
        void registerUserAccount_success_withVerificationEmail() throws Exception {
            // Given
            User newUser = UserTestDataBuilder.aUser()
                    .withEmail(testUserDto.getEmail())
                    .withFirstName(testUserDto.getFirstName())
                    .withLastName(testUserDto.getLastName())
                    .disabled() // User not enabled until email verification
                    .build();

            when(userService.registerNewUserAccount(any(UserDto.class))).thenReturn(newUser);
            when(passwordPolicyService.validate(any(), anyString(), anyString(), any(Locale.class)))
                    .thenReturn(Collections.emptyList());

            // When & Then
            MvcResult result = mockMvc.perform(post("/user/registration")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(testUserDto))
                    .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.messages[0]").value("Registration Successful!"))
                    .andExpect(jsonPath("$.redirectUrl").value("/user/registration-pending.html"))
                    .andReturn();

            // Verify event publishing
            verify(eventPublisher, times(2)).publishEvent(any());
            
            // Verify specific event types
            ArgumentCaptor<OnRegistrationCompleteEvent> registrationCaptor = ArgumentCaptor.forClass(OnRegistrationCompleteEvent.class);
            verify(eventPublisher).publishEvent(registrationCaptor.capture());
            OnRegistrationCompleteEvent registrationEvent = registrationCaptor.getValue();
            assertThat(registrationEvent.getUser()).isEqualTo(newUser);
            
            ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(eventPublisher).publishEvent(auditCaptor.capture());
            AuditEvent auditEvent = auditCaptor.getValue();
            assertThat(auditEvent.getAction()).isEqualTo("Registration");
            assertThat(auditEvent.getActionStatus()).isEqualTo("Success");
        }

        @Test
        @DisplayName("POST /user/registration - successful registration with auto-login")
        void registerUserAccount_success_withAutoLogin() throws Exception {
            // Given
            User newUser = UserTestDataBuilder.aUser()
                    .withEmail(testUserDto.getEmail())
                    .enabled() // User is immediately enabled
                    .build();

            when(userService.registerNewUserAccount(any(UserDto.class))).thenReturn(newUser);
            when(passwordPolicyService.validate(any(), anyString(), anyString(), any(Locale.class)))
                    .thenReturn(Collections.emptyList());

            // When & Then
            mockMvc.perform(post("/user/registration")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(testUserDto))
                    .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.redirectUrl").value("/user/registration-complete.html"));

            // Verify auto-login was called
            verify(userService).authWithoutPassword(newUser);
        }

        @Test
        @DisplayName("POST /user/registration - user already exists")
        void registerUserAccount_userAlreadyExists() throws Exception {
            // Given
            when(userService.registerNewUserAccount(any(UserDto.class)))
                    .thenThrow(new UserAlreadyExistException("User already exists"));
            when(passwordPolicyService.validate(any(), anyString(), anyString(), any(Locale.class)))
                    .thenReturn(Collections.emptyList());

            // When & Then
            mockMvc.perform(post("/user/registration")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(testUserDto))
                    .with(csrf()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value(2))
                    .andExpect(jsonPath("$.messages[0]").value("An account already exists for the email address"));
        }

        @Test
        @DisplayName("POST /user/registration - missing email")
        void registerUserAccount_missingEmail() throws Exception {
            // Given
            testUserDto.setEmail(null);

            // When & Then - validation should reject null email with 400 Bad Request
            mockMvc.perform(post("/user/registration")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(testUserDto))
                    .with(csrf()))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("POST /user/registration - missing password")
        void registerUserAccount_missingPassword() throws Exception {
            // Given
            testUserDto.setPassword(null);

            // When & Then - validation should reject null password with 400 Bad Request
            mockMvc.perform(post("/user/registration")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(testUserDto))
                    .with(csrf()))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("POST /user/registration - unexpected error")
        void registerUserAccount_unexpectedError() throws Exception {
            // Given
            when(userService.registerNewUserAccount(any(UserDto.class)))
                    .thenThrow(new RuntimeException("Database error"));
            when(passwordPolicyService.validate(any(), anyString(), anyString(), any(Locale.class)))
                    .thenReturn(Collections.emptyList());

            // When & Then
            mockMvc.perform(post("/user/registration")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(testUserDto))
                    .with(csrf()))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value(5))
                    .andExpect(jsonPath("$.messages[0]").value("System Error!"));
        }
    }

    @Nested
    @DisplayName("Resend Registration Token Tests")
    class ResendRegistrationTokenTests {

        @Test
        @DisplayName("POST /user/resendRegistrationToken - success")
        void resendRegistrationToken_success() throws Exception {
            // Given
            User unverifiedUser = UserTestDataBuilder.aUser()
                    .withEmail(testUserDto.getEmail())
                    .disabled()
                    .build();
            when(userService.findUserByEmail(testUserDto.getEmail())).thenReturn(unverifiedUser);

            // When & Then
            mockMvc.perform(post("/user/resendRegistrationToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(testUserDto))
                    .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.messages[0]").value("Verification Email Resent Successfully!"));

            verify(userEmailService).sendRegistrationVerificationEmail(eq(unverifiedUser), anyString());
        }

        @Test
        @DisplayName("POST /user/resendRegistrationToken - account already verified")
        void resendRegistrationToken_alreadyVerified() throws Exception {
            // Given
            when(userService.findUserByEmail(testUserDto.getEmail())).thenReturn(testUser); // enabled user

            // When & Then
            mockMvc.perform(post("/user/resendRegistrationToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(testUserDto))
                    .with(csrf()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value(1))
                    .andExpect(jsonPath("$.messages[0]").value("Account is already verified."));
        }

        @Test
        @DisplayName("POST /user/resendRegistrationToken - user not found")
        void resendRegistrationToken_userNotFound() throws Exception {
            // Given
            when(userService.findUserByEmail(testUserDto.getEmail())).thenReturn(null);

            // When & Then
            mockMvc.perform(post("/user/resendRegistrationToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(testUserDto))
                    .with(csrf()))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value(2));
        }
    }

    @Nested
    @DisplayName("Password Management Tests")
    class PasswordManagementTests {

        @Test
        @DisplayName("POST /user/resetPassword - success")
        void resetPassword_success() throws Exception {
            // Given
            when(userService.findUserByEmail(testUserDto.getEmail())).thenReturn(testUser);

            // When & Then
            mockMvc.perform(post("/user/resetPassword")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(testUserDto))
                    .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.messages[0]").value("If account exists, password reset email has been sent!"));

            verify(userEmailService).sendForgotPasswordVerificationEmail(eq(testUser), anyString());
        }

        @Test
        @DisplayName("POST /user/resetPassword - user not found (still returns success)")
        void resetPassword_userNotFound() throws Exception {
            // Given
            when(userService.findUserByEmail(testUserDto.getEmail())).thenReturn(null);

            // When & Then
            mockMvc.perform(post("/user/resetPassword")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(testUserDto))
                    .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.messages[0]").value("If account exists, password reset email has been sent!"));

            verify(userEmailService, never()).sendForgotPasswordVerificationEmail(any(), any());
        }

        @Test
        @DisplayName("POST /user/updatePassword - success")
        void updatePassword_success() throws Exception {
            // Given
            PasswordDto passwordDto = new PasswordDto();
            passwordDto.setOldPassword("oldPassword");
            passwordDto.setNewPassword("newPassword123");

            // Mock the principal resolver to return our test user
            mockMvc = MockMvcBuilders.standaloneSetup(userAPI)
                    .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                        @Override
                        public boolean supportsParameter(org.springframework.core.MethodParameter parameter) {
                            return parameter.getParameterType().equals(DSUserDetails.class);
                        }
                        
                        @Override
                        public Object resolveArgument(org.springframework.core.MethodParameter parameter,
                                org.springframework.web.method.support.ModelAndViewContainer mavContainer,
                                org.springframework.web.context.request.NativeWebRequest webRequest,
                                org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
                            return testUserDetails;
                        }
                    })
                    .setControllerAdvice(new TestExceptionHandler())
                    .build();

            when(messageSource.getMessage(eq("message.update-password.success"), any(), any(Locale.class)))
                    .thenReturn("Password updated successfully");
            when(userService.checkIfValidOldPassword(any(User.class), eq("oldPassword"))).thenReturn(true);

            // When & Then
            mockMvc.perform(post("/user/updatePassword")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(passwordDto))
                    .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.messages[0]").value("Password updated successfully"));

            verify(userService).changeUserPassword(eq(testUser), eq("newPassword123"));
        }

        @Test
        @DisplayName("POST /user/updatePassword - invalid old password")
        void updatePassword_invalidOldPassword() throws Exception {
            // Given
            PasswordDto passwordDto = new PasswordDto();
            passwordDto.setOldPassword("wrongPassword");
            passwordDto.setNewPassword("newPassword123");

            // Mock the principal resolver to return our test user
            mockMvc = MockMvcBuilders.standaloneSetup(userAPI)
                    .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                        @Override
                        public boolean supportsParameter(org.springframework.core.MethodParameter parameter) {
                            return parameter.getParameterType().equals(DSUserDetails.class);
                        }
                        
                        @Override
                        public Object resolveArgument(org.springframework.core.MethodParameter parameter,
                                org.springframework.web.method.support.ModelAndViewContainer mavContainer,
                                org.springframework.web.context.request.NativeWebRequest webRequest,
                                org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
                            return testUserDetails;
                        }
                    })
                    .setControllerAdvice(new TestExceptionHandler())
                    .build();

            when(messageSource.getMessage(eq("message.update-password.invalid-old"), any(), any(Locale.class)))
                    .thenReturn("Invalid old password");
            when(userService.checkIfValidOldPassword(any(User.class), eq("wrongPassword"))).thenReturn(false);

            // When & Then
            mockMvc.perform(post("/user/updatePassword")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(passwordDto))
                    .with(csrf()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value(1))
                    .andExpect(jsonPath("$.messages[0]").value("Invalid old password"));
        }

        @Test
        @DisplayName("POST /user/updatePassword - not authenticated")
        void updatePassword_notAuthenticated() throws Exception {
            // Given
            PasswordDto passwordDto = new PasswordDto();
            passwordDto.setOldPassword("oldPassword");
            passwordDto.setNewPassword("newPassword123");

            // When & Then
            mockMvc.perform(post("/user/updatePassword")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(passwordDto))
                    .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value(401))
                    .andExpect(jsonPath("$.messages[0]").value("User not logged in."));
        }
    }

    @Nested
    @DisplayName("User Profile Tests")
    class UserProfileTests {

        @Test
        @DisplayName("POST /user/updateUser - success")
        void updateUser_success() throws Exception {
            // Given
            UserProfileUpdateDto updateDto = new UserProfileUpdateDto();
            updateDto.setFirstName("UpdatedFirst");
            updateDto.setLastName("UpdatedLast");

            // Mock the principal resolver to return our test user
            mockMvc = MockMvcBuilders.standaloneSetup(userAPI)
                    .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                        @Override
                        public boolean supportsParameter(org.springframework.core.MethodParameter parameter) {
                            return parameter.getParameterType().equals(DSUserDetails.class);
                        }
                        
                        @Override
                        public Object resolveArgument(org.springframework.core.MethodParameter parameter,
                                org.springframework.web.method.support.ModelAndViewContainer mavContainer,
                                org.springframework.web.context.request.NativeWebRequest webRequest,
                                org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
                            return testUserDetails;
                        }
                    })
                    .setControllerAdvice(new TestExceptionHandler())
                    .build();

            when(messageSource.getMessage(eq("message.update-user.success"), any(), any(Locale.class)))
                    .thenReturn("Profile updated successfully");
            when(userService.saveRegisteredUser(any(User.class))).thenReturn(testUser);

            // When & Then
            mockMvc.perform(post("/user/updateUser")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateDto))
                    .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.messages[0]").value("Profile updated successfully"));

            verify(userService).saveRegisteredUser(argThat(user -> 
                user.getFirstName().equals("UpdatedFirst") && 
                user.getLastName().equals("UpdatedLast")
            ));
        }

        @Test
        @DisplayName("POST /user/updateUser - not authenticated")
        void updateUser_notAuthenticated() throws Exception {
            // Given
            UserProfileUpdateDto updateDto = new UserProfileUpdateDto();
            updateDto.setFirstName("UpdatedFirst");
            updateDto.setLastName("UpdatedLast");

            // When & Then
            mockMvc.perform(post("/user/updateUser")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateDto))
                    .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value(401))
                    .andExpect(jsonPath("$.messages[0]").value("User not logged in."));
        }

        @Test
        @DisplayName("POST /user/updateUser - validation fails with blank firstName")
        void updateUser_blankFirstName_fails() throws Exception {
            // Given
            UserProfileUpdateDto updateDto = new UserProfileUpdateDto();
            updateDto.setFirstName("");  // Blank - should fail validation
            updateDto.setLastName("UpdatedLast");

            // Create a validator for the standalone setup
            LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
            validator.afterPropertiesSet();

            // Mock the principal resolver to return our test user
            mockMvc = MockMvcBuilders.standaloneSetup(userAPI)
                    .setValidator(validator)
                    .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                        @Override
                        public boolean supportsParameter(org.springframework.core.MethodParameter parameter) {
                            return parameter.getParameterType().equals(DSUserDetails.class);
                        }

                        @Override
                        public Object resolveArgument(org.springframework.core.MethodParameter parameter,
                                org.springframework.web.method.support.ModelAndViewContainer mavContainer,
                                org.springframework.web.context.request.NativeWebRequest webRequest,
                                org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
                            return testUserDetails;
                        }
                    })
                    .setControllerAdvice(new TestExceptionHandler())
                    .build();

            // When & Then - validation should fail
            mockMvc.perform(post("/user/updateUser")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateDto))
                    .with(csrf()))
                    .andExpect(status().isBadRequest());

            verify(userService, never()).saveRegisteredUser(any(User.class));
        }

        @Test
        @DisplayName("POST /user/updateUser - validation fails with blank lastName")
        void updateUser_blankLastName_fails() throws Exception {
            // Given
            UserProfileUpdateDto updateDto = new UserProfileUpdateDto();
            updateDto.setFirstName("UpdatedFirst");
            updateDto.setLastName("");  // Blank - should fail validation

            // Create a validator for the standalone setup
            LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
            validator.afterPropertiesSet();

            // Mock the principal resolver to return our test user
            mockMvc = MockMvcBuilders.standaloneSetup(userAPI)
                    .setValidator(validator)
                    .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                        @Override
                        public boolean supportsParameter(org.springframework.core.MethodParameter parameter) {
                            return parameter.getParameterType().equals(DSUserDetails.class);
                        }

                        @Override
                        public Object resolveArgument(org.springframework.core.MethodParameter parameter,
                                org.springframework.web.method.support.ModelAndViewContainer mavContainer,
                                org.springframework.web.context.request.NativeWebRequest webRequest,
                                org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
                            return testUserDetails;
                        }
                    })
                    .setControllerAdvice(new TestExceptionHandler())
                    .build();

            // When & Then - validation should fail
            mockMvc.perform(post("/user/updateUser")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateDto))
                    .with(csrf()))
                    .andExpect(status().isBadRequest());

            verify(userService, never()).saveRegisteredUser(any(User.class));
        }

        @Test
        @DisplayName("POST /user/updateUser - validation fails with firstName exceeding 50 characters")
        void updateUser_firstNameTooLong_fails() throws Exception {
            // Given
            UserProfileUpdateDto updateDto = new UserProfileUpdateDto();
            updateDto.setFirstName("A".repeat(51));  // 51 chars - exceeds 50 char limit
            updateDto.setLastName("UpdatedLast");

            // Create a validator for the standalone setup
            LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
            validator.afterPropertiesSet();

            // Mock the principal resolver to return our test user
            mockMvc = MockMvcBuilders.standaloneSetup(userAPI)
                    .setValidator(validator)
                    .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                        @Override
                        public boolean supportsParameter(org.springframework.core.MethodParameter parameter) {
                            return parameter.getParameterType().equals(DSUserDetails.class);
                        }

                        @Override
                        public Object resolveArgument(org.springframework.core.MethodParameter parameter,
                                org.springframework.web.method.support.ModelAndViewContainer mavContainer,
                                org.springframework.web.context.request.NativeWebRequest webRequest,
                                org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
                            return testUserDetails;
                        }
                    })
                    .setControllerAdvice(new TestExceptionHandler())
                    .build();

            // When & Then - validation should fail
            mockMvc.perform(post("/user/updateUser")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateDto))
                    .with(csrf()))
                    .andExpect(status().isBadRequest());

            verify(userService, never()).saveRegisteredUser(any(User.class));
        }

        @Test
        @DisplayName("POST /user/updateUser - validation fails with null fields")
        void updateUser_nullFields_fails() throws Exception {
            // Given
            UserProfileUpdateDto updateDto = new UserProfileUpdateDto();
            // Both fields are null - should fail validation

            // Create a validator for the standalone setup
            LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
            validator.afterPropertiesSet();

            // Mock the principal resolver to return our test user
            mockMvc = MockMvcBuilders.standaloneSetup(userAPI)
                    .setValidator(validator)
                    .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                        @Override
                        public boolean supportsParameter(org.springframework.core.MethodParameter parameter) {
                            return parameter.getParameterType().equals(DSUserDetails.class);
                        }

                        @Override
                        public Object resolveArgument(org.springframework.core.MethodParameter parameter,
                                org.springframework.web.method.support.ModelAndViewContainer mavContainer,
                                org.springframework.web.context.request.NativeWebRequest webRequest,
                                org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
                            return testUserDetails;
                        }
                    })
                    .setControllerAdvice(new TestExceptionHandler())
                    .build();

            // When & Then - validation should fail
            mockMvc.perform(post("/user/updateUser")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateDto))
                    .with(csrf()))
                    .andExpect(status().isBadRequest());

            verify(userService, never()).saveRegisteredUser(any(User.class));
        }

        @Test
        @DisplayName("POST /user/updateUser - accepts maximum valid length names")
        void updateUser_maxValidLength_succeeds() throws Exception {
            // Given
            UserProfileUpdateDto updateDto = new UserProfileUpdateDto();
            updateDto.setFirstName("A".repeat(50));  // Exactly 50 chars - should be valid
            updateDto.setLastName("B".repeat(50));   // Exactly 50 chars - should be valid

            // Mock the principal resolver to return our test user
            mockMvc = MockMvcBuilders.standaloneSetup(userAPI)
                    .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                        @Override
                        public boolean supportsParameter(org.springframework.core.MethodParameter parameter) {
                            return parameter.getParameterType().equals(DSUserDetails.class);
                        }

                        @Override
                        public Object resolveArgument(org.springframework.core.MethodParameter parameter,
                                org.springframework.web.method.support.ModelAndViewContainer mavContainer,
                                org.springframework.web.context.request.NativeWebRequest webRequest,
                                org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
                            return testUserDetails;
                        }
                    })
                    .setControllerAdvice(new TestExceptionHandler())
                    .build();

            when(messageSource.getMessage(eq("message.update-user.success"), any(), any(Locale.class)))
                    .thenReturn("Profile updated successfully");
            when(userService.saveRegisteredUser(any(User.class))).thenReturn(testUser);

            // When & Then
            mockMvc.perform(post("/user/updateUser")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateDto))
                    .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(userService).saveRegisteredUser(any(User.class));
        }
    }

    @Nested
    @DisplayName("Account Deletion Tests")
    class AccountDeletionTests {

        @Test
        @DisplayName("DELETE /user/deleteAccount - success")
        void deleteAccount_success() throws Exception {
            // Mock the principal resolver to return our test user
            mockMvc = MockMvcBuilders.standaloneSetup(userAPI)
                    .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                        @Override
                        public boolean supportsParameter(org.springframework.core.MethodParameter parameter) {
                            return parameter.getParameterType().equals(DSUserDetails.class);
                        }
                        
                        @Override
                        public Object resolveArgument(org.springframework.core.MethodParameter parameter,
                                org.springframework.web.method.support.ModelAndViewContainer mavContainer,
                                org.springframework.web.context.request.NativeWebRequest webRequest,
                                org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
                            return testUserDetails;
                        }
                    })
                    .setControllerAdvice(new TestExceptionHandler())
                    .build();

            // When & Then
            mockMvc.perform(delete("/user/deleteAccount")
                    .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.messages[0]").value("Account Deleted"));

            verify(userService).deleteOrDisableUser(testUser);
        }

        @Test
        @DisplayName("DELETE /user/deleteAccount - not authenticated")
        void deleteAccount_notAuthenticated() throws Exception {
            // When & Then
            mockMvc.perform(delete("/user/deleteAccount")
                    .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value(401))
                    .andExpect(jsonPath("$.messages[0]").value("User not logged in."));
        }
    }

    @Nested
    @DisplayName("Security and Validation Tests")
    class SecurityValidationTests {

        @Test
        @DisplayName("POST /user/registration - CSRF protection (standalone MockMvc limitation)")
        void registration_csrfProtection() throws Exception {
            // Note: In standalone MockMvc setup, CSRF protection is not enabled by default.
            // This test verifies basic request handling. Actual CSRF protection should be
            // tested in integration tests using @WebMvcTest or full Spring context.

            // Given - simulating missing required fields to trigger validation error
            testUserDto.setEmail(null);

            // When & Then - without CSRF token, request still reaches validation
            // which fails with 400 Bad Request for missing email
            mockMvc.perform(post("/user/registration")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(testUserDto)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("POST /user/registration - content type validation")
        void registration_contentTypeValidation() throws Exception {
            // When & Then
            mockMvc.perform(post("/user/registration")
                    .contentType(MediaType.TEXT_PLAIN)
                    .content("invalid content")
                    .with(csrf()))
                    .andExpect(status().isUnsupportedMediaType());
        }
    }
}