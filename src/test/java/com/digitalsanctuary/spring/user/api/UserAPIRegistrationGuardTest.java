package com.digitalsanctuary.spring.user.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.digitalsanctuary.spring.user.dto.PasswordlessRegistrationDto;
import com.digitalsanctuary.spring.user.dto.UserDto;
import com.digitalsanctuary.spring.user.exceptions.UserAlreadyExistException;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.registration.RegistrationContext;
import com.digitalsanctuary.spring.user.registration.RegistrationDecision;
import com.digitalsanctuary.spring.user.registration.RegistrationGuard;
import com.digitalsanctuary.spring.user.service.PasswordPolicyService;
import com.digitalsanctuary.spring.user.service.UserEmailService;
import com.digitalsanctuary.spring.user.service.UserService;
import com.digitalsanctuary.spring.user.service.WebAuthnCredentialManagementService;
import com.digitalsanctuary.spring.user.test.builders.UserTestDataBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserAPI RegistrationGuard Tests")
class UserAPIRegistrationGuardTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

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

    @Mock
    private ObjectProvider<WebAuthnCredentialManagementService> webAuthnCredentialManagementServiceProvider;

    @Mock
    private RegistrationGuard registrationGuard;

    @InjectMocks
    private UserAPI userAPI;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(userAPI, "registrationPendingURI", "/user/registration-pending.html");
        ReflectionTestUtils.setField(userAPI, "registrationSuccessURI", "/user/registration-complete.html");
        ReflectionTestUtils.setField(userAPI, "forgotPasswordPendingURI", "/user/forgot-password-pending.html");

        mockMvc = MockMvcBuilders.standaloneSetup(userAPI).build();
    }

    @Nested
    @DisplayName("Form Registration Guard Tests")
    class FormRegistrationGuardTests {

        @Test
        @DisplayName("Should reject form registration when guard denies")
        void shouldRejectFormRegistrationWhenGuardDenies() throws Exception {
            UserDto userDto = new UserDto();
            userDto.setEmail("test@example.com");
            userDto.setFirstName("Test");
            userDto.setLastName("User");
            userDto.setPassword("password123");
            userDto.setMatchingPassword("password123");
            userDto.setRole(1);

            when(passwordPolicyService.validate(any(), anyString(), anyString(), any(Locale.class)))
                    .thenReturn(Collections.emptyList());
            when(registrationGuard.evaluate(any(RegistrationContext.class)))
                    .thenReturn(RegistrationDecision.deny("Registration is by invitation only"));

            mockMvc.perform(post("/user/registration")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(userDto))
                            .with(csrf()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value(6))
                    .andExpect(jsonPath("$.messages[0]").value("Registration is by invitation only"));
        }

        @Test
        @DisplayName("Should allow form registration when guard allows")
        void shouldAllowFormRegistrationWhenGuardAllows() throws Exception {
            UserDto userDto = new UserDto();
            userDto.setEmail("test@example.com");
            userDto.setFirstName("Test");
            userDto.setLastName("User");
            userDto.setPassword("password123");
            userDto.setMatchingPassword("password123");
            userDto.setRole(1);

            User registeredUser = UserTestDataBuilder.aUser()
                    .withEmail("test@example.com")
                    .disabled()
                    .build();

            when(passwordPolicyService.validate(any(), anyString(), anyString(), any(Locale.class)))
                    .thenReturn(Collections.emptyList());
            when(registrationGuard.evaluate(any(RegistrationContext.class)))
                    .thenReturn(RegistrationDecision.allow());
            when(userService.registerNewUserAccount(any(UserDto.class))).thenReturn(registeredUser);

            mockMvc.perform(post("/user/registration")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(userDto))
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Nested
    @DisplayName("Passwordless Registration Guard Tests")
    class PasswordlessRegistrationGuardTests {

        @Test
        @DisplayName("Should reject passwordless registration when guard denies")
        void shouldRejectPasswordlessRegistrationWhenGuardDenies() throws Exception {
            PasswordlessRegistrationDto dto = new PasswordlessRegistrationDto();
            dto.setEmail("test@example.com");
            dto.setFirstName("Test");
            dto.setLastName("User");

            WebAuthnCredentialManagementService webAuthnService = org.mockito.Mockito.mock(WebAuthnCredentialManagementService.class);
            when(webAuthnCredentialManagementServiceProvider.getIfAvailable()).thenReturn(webAuthnService);
            when(registrationGuard.evaluate(any(RegistrationContext.class)))
                    .thenReturn(RegistrationDecision.deny("Beta access required"));

            mockMvc.perform(post("/user/registration/passwordless")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto))
                            .with(csrf()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value(6))
                    .andExpect(jsonPath("$.messages[0]").value("Beta access required"));
        }

        @Test
        @DisplayName("Should allow passwordless registration when guard allows")
        void shouldAllowPasswordlessRegistrationWhenGuardAllows() throws Exception {
            PasswordlessRegistrationDto dto = new PasswordlessRegistrationDto();
            dto.setEmail("test@example.com");
            dto.setFirstName("Test");
            dto.setLastName("User");

            User registeredUser = UserTestDataBuilder.aUser()
                    .withEmail("test@example.com")
                    .disabled()
                    .build();

            WebAuthnCredentialManagementService webAuthnService = org.mockito.Mockito.mock(WebAuthnCredentialManagementService.class);
            when(webAuthnCredentialManagementServiceProvider.getIfAvailable()).thenReturn(webAuthnService);
            when(registrationGuard.evaluate(any(RegistrationContext.class)))
                    .thenReturn(RegistrationDecision.allow());
            when(userService.registerPasswordlessAccount(any(PasswordlessRegistrationDto.class)))
                    .thenReturn(registeredUser);

            mockMvc.perform(post("/user/registration/passwordless")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto))
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }
}
