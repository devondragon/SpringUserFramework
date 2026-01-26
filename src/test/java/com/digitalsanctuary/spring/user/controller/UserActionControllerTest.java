package com.digitalsanctuary.spring.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

import com.digitalsanctuary.spring.user.audit.AuditEvent;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.service.UserService;
import com.digitalsanctuary.spring.user.service.UserService.TokenValidationResult;
import com.digitalsanctuary.spring.user.service.UserVerificationService;
import com.digitalsanctuary.spring.user.test.builders.UserTestDataBuilder;

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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Locale;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserActionController Tests")
class UserActionControllerTest {
    
    private MockMvc mockMvc;
    
    @Mock
    private UserService userService;
    
    @Mock
    private UserVerificationService userVerificationService;
    
    @Mock
    private MessageSource messageSource;
    
    @Mock
    private ApplicationEventPublisher eventPublisher;
    
    @InjectMocks
    private UserActionController userActionController;
    
    private User testUser;
    
    @BeforeEach
    void setUp() {
        // Set field values using reflection
        ReflectionTestUtils.setField(userActionController, "registrationPendingURI", "/user/registration-pending.html");
        ReflectionTestUtils.setField(userActionController, "registrationSuccessURI", "/user/registration-complete.html");
        ReflectionTestUtils.setField(userActionController, "registrationNewVerificationURI", "/user/request-new-verification-email.html");
        ReflectionTestUtils.setField(userActionController, "forgotPasswordPendingURI", "/user/forgot-password-pending.html");
        ReflectionTestUtils.setField(userActionController, "forgotPasswordChangeURI", "/user/forgot-password-change.html");
        
        mockMvc = MockMvcBuilders.standaloneSetup(userActionController).build();
        
        testUser = UserTestDataBuilder.aUser()
                .withId(1L)
                .withEmail("test@example.com")
                .withFirstName("Test")
                .withLastName("User")
                .enabled()
                .build();
    }
    
    @Nested
    @DisplayName("Password Reset Token Validation Tests")
    class PasswordResetTokenValidationTests {
        
        @Test
        @DisplayName("Should redirect to change password page for valid token")
        void showChangePasswordPage_validToken_redirectsToChangePasswordPage() throws Exception {
            // Given
            String token = "valid-token-123";
            when(userService.validatePasswordResetToken(token)).thenReturn(TokenValidationResult.VALID);
            
            // When & Then
            mockMvc.perform(get("/user/changePassword")
                    .param("token", token))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/user/forgot-password-change.html?token=" + token))
                    .andExpect(model().attribute("token", token));
            
            // Verify audit event
            ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(eventPublisher).publishEvent(auditCaptor.capture());
            AuditEvent auditEvent = auditCaptor.getValue();
            assert auditEvent.getAction().equals("showChangePasswordPage");
            assert auditEvent.getActionStatus().equals("Success");
        }
        
        @Test
        @DisplayName("Should redirect to index with error for expired token")
        void showChangePasswordPage_expiredToken_redirectsToIndexWithError() throws Exception {
            // Given
            String token = "expired-token-123";
            when(userService.validatePasswordResetToken(token)).thenReturn(TokenValidationResult.EXPIRED);
            
            // When & Then
            mockMvc.perform(get("/user/changePassword")
                    .param("token", token))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/index.html?messageKey=auth.message.expired"))
                    .andExpect(model().attribute("messageKey", "auth.message.expired"));
            
            verify(eventPublisher).publishEvent(any(AuditEvent.class));
        }
        
        @Test
        @DisplayName("Should redirect to index with error for invalid token")
        void showChangePasswordPage_invalidToken_redirectsToIndexWithError() throws Exception {
            // Given
            String token = "invalid-token-123";
            when(userService.validatePasswordResetToken(token)).thenReturn(TokenValidationResult.INVALID_TOKEN);
            
            // When & Then
            mockMvc.perform(get("/user/changePassword")
                    .param("token", token))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/index.html?messageKey=auth.message.invalidToken"))
                    .andExpect(model().attribute("messageKey", "auth.message.invalidToken"));
        }
        
        @Test
        @DisplayName("Should handle missing token parameter")
        void showChangePasswordPage_missingToken_returns400() throws Exception {
            // When & Then
            mockMvc.perform(get("/user/changePassword"))
                    .andExpect(status().isBadRequest());
        }
    }
    
    @Nested
    @DisplayName("Registration Confirmation Tests")
    class RegistrationConfirmationTests {
        
        @Test
        @DisplayName("Should confirm registration for valid token")
        void confirmRegistration_validToken_confirmsAndAuthenticatesUser() throws Exception {
            // Given
            String token = "valid-reg-token-123";
            when(userVerificationService.validateVerificationToken(token)).thenReturn(TokenValidationResult.VALID);
            when(userVerificationService.getUserByVerificationToken(token)).thenReturn(testUser);
            when(messageSource.getMessage(eq("message.account.verified"), any(), any(Locale.class)))
                    .thenReturn("Account verified successfully");
            
            // When & Then
            mockMvc.perform(get("/user/registrationConfirm")
                    .param("token", token))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/user/registration-complete.html?lang=en&message=Account+verified+successfully"))
                    .andExpect(model().attribute("message", "Account verified successfully"));
            
            // Verify interactions
            verify(userService).authWithoutPassword(testUser);
            verify(userVerificationService).deleteVerificationToken(token);
            
            // Verify audit event
            ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(eventPublisher).publishEvent(auditCaptor.capture());
            AuditEvent auditEvent = auditCaptor.getValue();
            assert auditEvent.getAction().equals("Registration Confirmation");
            assert auditEvent.getActionStatus().equals("Success");
            assert auditEvent.getUser().equals(testUser);
        }
        
        @Test
        @DisplayName("Should handle expired registration token")
        void confirmRegistration_expiredToken_redirectsToNewVerification() throws Exception {
            // Given
            String token = "expired-reg-token-123";
            when(userVerificationService.validateVerificationToken(token)).thenReturn(TokenValidationResult.EXPIRED);
            
            // When & Then
            mockMvc.perform(get("/user/registrationConfirm")
                    .param("token", token))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/user/request-new-verification-email.html?lang=en&messageKey=auth.message.expired&expired=true&token=" + token))
                    .andExpect(model().attribute("expired", true))
                    .andExpect(model().attribute("token", token));
            
            // Verify no user operations were performed
            verify(userService, never()).authWithoutPassword(any());
            verify(userVerificationService, never()).deleteVerificationToken(anyString());
        }
        
        @Test
        @DisplayName("Should handle invalid registration token")
        void confirmRegistration_invalidToken_redirectsToNewVerification() throws Exception {
            // Given
            String token = "invalid-reg-token-123";
            when(userVerificationService.validateVerificationToken(token)).thenReturn(TokenValidationResult.INVALID_TOKEN);
            
            // When & Then
            mockMvc.perform(get("/user/registrationConfirm")
                    .param("token", token))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/user/request-new-verification-email.html?lang=en&messageKey=auth.message.invalidToken&expired=false&token=" + token))
                    .andExpect(model().attribute("expired", false))
                    .andExpect(model().attribute("token", token));
        }
        
        @Test
        @DisplayName("Should handle null user for valid token")
        void confirmRegistration_validTokenButNullUser_redirectsToSuccessWithoutAuth() throws Exception {
            // Given
            String token = "valid-but-no-user-token";
            when(userVerificationService.validateVerificationToken(token)).thenReturn(TokenValidationResult.VALID);
            when(userVerificationService.getUserByVerificationToken(token)).thenReturn(null);
            when(messageSource.getMessage(eq("message.account.verified"), any(), any(Locale.class)))
                    .thenReturn("Account verified successfully");
            
            // When & Then
            mockMvc.perform(get("/user/registrationConfirm")
                    .param("token", token))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/user/registration-complete.html?lang=en&message=Account+verified+successfully"));
            
            // Verify no authentication was attempted
            verify(userService, never()).authWithoutPassword(any());
            // Token should NOT be deleted when user is null
            verify(userVerificationService, never()).deleteVerificationToken(token);
        }
        
        @Test
        @DisplayName("Should handle missing token parameter")
        void confirmRegistration_missingToken_returns400() throws Exception {
            // When & Then
            mockMvc.perform(get("/user/registrationConfirm"))
                    .andExpect(status().isBadRequest());
        }
        
        @Test
        @DisplayName("Should include locale in model")
        void confirmRegistration_withLocale_includesLocaleInModel() throws Exception {
            // Given
            String token = "valid-token-123";
            when(userVerificationService.validateVerificationToken(token)).thenReturn(TokenValidationResult.VALID);
            when(userVerificationService.getUserByVerificationToken(token)).thenReturn(testUser);
            when(messageSource.getMessage(eq("message.account.verified"), any(), any(Locale.class)))
                    .thenReturn("Account verified successfully");
            
            // When & Then
            mockMvc.perform(get("/user/registrationConfirm")
                    .param("token", token)
                    .locale(Locale.FRENCH))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(model().attribute("lang", "fr"));
        }
    }
}