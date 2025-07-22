package com.digitalsanctuary.spring.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.digitalsanctuary.spring.user.audit.AuditEvent;
import com.digitalsanctuary.spring.user.mail.MailService;
import com.digitalsanctuary.spring.user.persistence.model.PasswordResetToken;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.repository.PasswordResetTokenRepository;
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

import java.util.Map;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserEmailService Tests")
class UserEmailServiceTest {

    @Mock
    private MailService mailService;

    @Mock
    private UserVerificationService userVerificationService;

    @Mock
    private PasswordResetTokenRepository passwordTokenRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private UserEmailService userEmailService;

    private User testUser;
    private String appUrl;

    @BeforeEach
    void setUp() {
        testUser = UserTestDataBuilder.aUser()
                .withId(1L)
                .withEmail("test@example.com")
                .withFirstName("Test")
                .withLastName("User")
                .enabled()
                .build();
        appUrl = "https://example.com";
    }

    @Nested
    @DisplayName("Password Reset Email Tests")
    class PasswordResetEmailTests {

        @Test
        @DisplayName("sendForgotPasswordVerificationEmail - sends email with correct parameters")
        void sendForgotPasswordVerificationEmail_sendsEmailWithCorrectParameters() {
            // When
            userEmailService.sendForgotPasswordVerificationEmail(testUser, appUrl);

            // Then
            // Verify password reset token was created
            ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
            verify(passwordTokenRepository).save(tokenCaptor.capture());
            PasswordResetToken savedToken = tokenCaptor.getValue();
            assertThat(savedToken.getUser()).isEqualTo(testUser);
            assertThat(savedToken.getToken()).isNotNull();
            assertThat(savedToken.getToken()).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

            // Verify audit event was published
            ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(eventPublisher).publishEvent(auditCaptor.capture());
            AuditEvent auditEvent = auditCaptor.getValue();
            assertThat(auditEvent.getUser()).isEqualTo(testUser);
            assertThat(auditEvent.getAction()).isEqualTo("sendForgotPasswordVerificationEmail");
            assertThat(auditEvent.getActionStatus()).isEqualTo("Success");
            assertThat(auditEvent.getMessage()).isEqualTo("Forgot password email to be sent.");

            // Verify email was sent with correct parameters
            ArgumentCaptor<Map<String, Object>> variablesCaptor = ArgumentCaptor.forClass(Map.class);
            verify(mailService).sendTemplateMessage(
                    eq(testUser.getEmail()),
                    eq("Password Reset"),
                    variablesCaptor.capture(),
                    eq("mail/forgot-password-token.html")
            );

            Map<String, Object> variables = variablesCaptor.getValue();
            assertThat(variables).containsKey("token");
            assertThat(variables).containsKey("appUrl");
            assertThat(variables).containsKey("confirmationUrl");
            assertThat(variables).containsKey("user");
            assertThat(variables.get("appUrl")).isEqualTo(appUrl);
            assertThat(variables.get("user")).isEqualTo(testUser);
            assertThat(variables.get("confirmationUrl")).asString()
                    .startsWith(appUrl + "/user/changePassword?token=");
        }

        @Test
        @DisplayName("sendForgotPasswordVerificationEmail - handles empty app URL")
        void sendForgotPasswordVerificationEmail_handlesEmptyAppUrl() {
            // Given
            String emptyUrl = "";

            // When
            userEmailService.sendForgotPasswordVerificationEmail(testUser, emptyUrl);

            // Then
            ArgumentCaptor<Map<String, Object>> variablesCaptor = ArgumentCaptor.forClass(Map.class);
            verify(mailService).sendTemplateMessage(
                    eq(testUser.getEmail()),
                    eq("Password Reset"),
                    variablesCaptor.capture(),
                    eq("mail/forgot-password-token.html")
            );

            Map<String, Object> variables = variablesCaptor.getValue();
            assertThat(variables.get("confirmationUrl")).asString()
                    .startsWith("/user/changePassword?token=");
        }

        @Test
        @DisplayName("createPasswordResetTokenForUser - creates and saves token")
        void createPasswordResetTokenForUser_createsAndSavesToken() {
            // Given
            String token = "test-token-123";

            // When
            userEmailService.createPasswordResetTokenForUser(testUser, token);

            // Then
            ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
            verify(passwordTokenRepository).save(tokenCaptor.capture());
            PasswordResetToken savedToken = tokenCaptor.getValue();
            assertThat(savedToken.getToken()).isEqualTo(token);
            assertThat(savedToken.getUser()).isEqualTo(testUser);
            assertThat(savedToken.getExpiryDate()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Registration Email Tests")
    class RegistrationEmailTests {

        @Test
        @DisplayName("sendRegistrationVerificationEmail - sends email with correct parameters")
        void sendRegistrationVerificationEmail_sendsEmailWithCorrectParameters() {
            // When
            userEmailService.sendRegistrationVerificationEmail(testUser, appUrl);

            // Then
            // Verify verification token was created
            ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
            verify(userVerificationService).createVerificationTokenForUser(eq(testUser), tokenCaptor.capture());
            String token = tokenCaptor.getValue();
            assertThat(token).isNotNull();
            assertThat(token).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

            // Verify email was sent with correct parameters
            ArgumentCaptor<Map<String, Object>> variablesCaptor = ArgumentCaptor.forClass(Map.class);
            verify(mailService).sendTemplateMessage(
                    eq(testUser.getEmail()),
                    eq("Registration Confirmation"),
                    variablesCaptor.capture(),
                    eq("mail/registration-token.html")
            );

            Map<String, Object> variables = variablesCaptor.getValue();
            assertThat(variables).containsKey("token");
            assertThat(variables).containsKey("appUrl");
            assertThat(variables).containsKey("confirmationUrl");
            assertThat(variables).containsKey("user");
            assertThat(variables.get("appUrl")).isEqualTo(appUrl);
            assertThat(variables.get("user")).isEqualTo(testUser);
            assertThat(variables.get("confirmationUrl")).asString()
                    .startsWith(appUrl + "/user/registrationConfirm?token=");
        }

        @Test
        @DisplayName("sendRegistrationVerificationEmail - handles null app URL")
        void sendRegistrationVerificationEmail_handlesNullAppUrl() {
            // When
            userEmailService.sendRegistrationVerificationEmail(testUser, null);

            // Then
            ArgumentCaptor<Map<String, Object>> variablesCaptor = ArgumentCaptor.forClass(Map.class);
            verify(mailService).sendTemplateMessage(
                    eq(testUser.getEmail()),
                    eq("Registration Confirmation"),
                    variablesCaptor.capture(),
                    eq("mail/registration-token.html")
            );

            Map<String, Object> variables = variablesCaptor.getValue();
            assertThat(variables.get("appUrl")).isNull();
            assertThat(variables.get("confirmationUrl")).asString()
                    .isEqualTo("null/user/registrationConfirm?token=" + variables.get("token"));
        }

        @Test
        @DisplayName("sendRegistrationVerificationEmail - uses different token for each call")
        void sendRegistrationVerificationEmail_usesDifferentTokenForEachCall() {
            // When
            userEmailService.sendRegistrationVerificationEmail(testUser, appUrl);
            userEmailService.sendRegistrationVerificationEmail(testUser, appUrl);

            // Then
            ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
            verify(userVerificationService, times(2))
                    .createVerificationTokenForUser(eq(testUser), tokenCaptor.capture());
            
            String token1 = tokenCaptor.getAllValues().get(0);
            String token2 = tokenCaptor.getAllValues().get(1);
            assertThat(token1).isNotEqualTo(token2);
        }
    }

    @Nested
    @DisplayName("Email Template Variable Tests")
    class EmailTemplateVariableTests {

        @Test
        @DisplayName("Email variables contain all required fields for password reset")
        void emailVariables_containAllRequiredFieldsForPasswordReset() {
            // When
            userEmailService.sendForgotPasswordVerificationEmail(testUser, appUrl);

            // Then
            ArgumentCaptor<Map<String, Object>> variablesCaptor = ArgumentCaptor.forClass(Map.class);
            verify(mailService).sendTemplateMessage(
                    any(),
                    any(),
                    variablesCaptor.capture(),
                    any()
            );

            Map<String, Object> variables = variablesCaptor.getValue();
            assertThat(variables).hasSize(4);
            assertThat(variables).containsKeys("token", "appUrl", "confirmationUrl", "user");
        }

        @Test
        @DisplayName("Email variables contain all required fields for registration")
        void emailVariables_containAllRequiredFieldsForRegistration() {
            // When
            userEmailService.sendRegistrationVerificationEmail(testUser, appUrl);

            // Then
            ArgumentCaptor<Map<String, Object>> variablesCaptor = ArgumentCaptor.forClass(Map.class);
            verify(mailService).sendTemplateMessage(
                    any(),
                    any(),
                    variablesCaptor.capture(),
                    any()
            );

            Map<String, Object> variables = variablesCaptor.getValue();
            assertThat(variables).hasSize(4);
            assertThat(variables).containsKeys("token", "appUrl", "confirmationUrl", "user");
        }

        @Test
        @DisplayName("Confirmation URL is properly constructed with token")
        void confirmationUrl_properlyConstructedWithToken() {
            // When
            userEmailService.sendForgotPasswordVerificationEmail(testUser, appUrl);

            // Then
            ArgumentCaptor<Map<String, Object>> variablesCaptor = ArgumentCaptor.forClass(Map.class);
            verify(mailService).sendTemplateMessage(
                    any(),
                    any(),
                    variablesCaptor.capture(),
                    any()
            );

            Map<String, Object> variables = variablesCaptor.getValue();
            String token = (String) variables.get("token");
            String confirmationUrl = (String) variables.get("confirmationUrl");
            assertThat(confirmationUrl).isEqualTo(appUrl + "/user/changePassword?token=" + token);
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("Multiple users can request password reset simultaneously")
        void multipleUsers_canRequestPasswordResetSimultaneously() {
            // Given
            User user1 = UserTestDataBuilder.aUser()
                    .withEmail("user1@example.com")
                    .build();
            User user2 = UserTestDataBuilder.aUser()
                    .withEmail("user2@example.com")
                    .build();

            // When
            userEmailService.sendForgotPasswordVerificationEmail(user1, appUrl);
            userEmailService.sendForgotPasswordVerificationEmail(user2, appUrl);

            // Then
            verify(passwordTokenRepository, times(2)).save(any(PasswordResetToken.class));
            verify(mailService).sendTemplateMessage(
                    eq("user1@example.com"),
                    eq("Password Reset"),
                    any(),
                    eq("mail/forgot-password-token.html")
            );
            verify(mailService).sendTemplateMessage(
                    eq("user2@example.com"),
                    eq("Password Reset"),
                    any(),
                    eq("mail/forgot-password-token.html")
            );
        }

        @Test
        @DisplayName("User can request both registration and password reset")
        void user_canRequestBothRegistrationAndPasswordReset() {
            // When
            userEmailService.sendRegistrationVerificationEmail(testUser, appUrl);
            userEmailService.sendForgotPasswordVerificationEmail(testUser, appUrl);

            // Then
            verify(userVerificationService).createVerificationTokenForUser(eq(testUser), any());
            verify(passwordTokenRepository).save(any(PasswordResetToken.class));
            verify(mailService, times(2)).sendTemplateMessage(
                    eq(testUser.getEmail()),
                    any(),
                    any(),
                    any()
            );
        }
    }
}