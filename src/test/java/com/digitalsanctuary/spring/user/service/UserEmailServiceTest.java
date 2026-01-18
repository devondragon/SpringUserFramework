package com.digitalsanctuary.spring.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.digitalsanctuary.spring.user.audit.AuditEvent;
import com.digitalsanctuary.spring.user.mail.MailService;
import com.digitalsanctuary.spring.user.persistence.model.PasswordResetToken;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.repository.PasswordResetTokenRepository;
import com.digitalsanctuary.spring.user.test.builders.UserTestDataBuilder;

import org.junit.jupiter.api.AfterEach;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;
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

    @Mock
    private SessionInvalidationService sessionInvalidationService;

    @InjectMocks
    private UserEmailService userEmailService;

    private User testUser;
    private User adminUser;
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

        // Set up admin user for SecurityContext mocking
        adminUser = UserTestDataBuilder.aUser()
                .withId(99L)
                .withEmail("admin@example.com")
                .withFirstName("Admin")
                .withLastName("User")
                .enabled()
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /**
     * Sets up a mock SecurityContext with the given user as the authenticated admin.
     */
    private void mockSecurityContext(User user) {
        DSUserDetails userDetails = new DSUserDetails(user);
        Authentication authentication = mock(Authentication.class, withSettings().lenient());
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(authentication.getName()).thenReturn(user.getEmail());

        SecurityContext securityContext = mock(SecurityContext.class, withSettings().lenient());
        when(securityContext.getAuthentication()).thenReturn(authentication);

        SecurityContextHolder.setContext(securityContext);
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
        @DisplayName("sendForgotPasswordVerificationEmail - rejects empty app URL")
        void sendForgotPasswordVerificationEmail_rejectsEmptyAppUrl() {
            // Given
            String emptyUrl = "";

            // When/Then
            assertThatThrownBy(() -> userEmailService.sendForgotPasswordVerificationEmail(testUser, emptyUrl))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid application URL");
        }

        @Test
        @DisplayName("sendForgotPasswordVerificationEmail - rejects null app URL")
        void sendForgotPasswordVerificationEmail_rejectsNullAppUrl() {
            // When/Then
            assertThatThrownBy(() -> userEmailService.sendForgotPasswordVerificationEmail(testUser, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid application URL");
        }

        @Test
        @DisplayName("sendForgotPasswordVerificationEmail - rejects javascript URL")
        void sendForgotPasswordVerificationEmail_rejectsJavascriptUrl() {
            // When/Then
            assertThatThrownBy(() -> userEmailService.sendForgotPasswordVerificationEmail(testUser, "javascript:alert('xss')"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid application URL");
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
        @DisplayName("sendRegistrationVerificationEmail - rejects null app URL")
        void sendRegistrationVerificationEmail_rejectsNullAppUrl() {
            // When/Then
            assertThatThrownBy(() -> userEmailService.sendRegistrationVerificationEmail(testUser, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid application URL");
        }

        @Test
        @DisplayName("sendRegistrationVerificationEmail - rejects empty app URL")
        void sendRegistrationVerificationEmail_rejectsEmptyAppUrl() {
            // When/Then
            assertThatThrownBy(() -> userEmailService.sendRegistrationVerificationEmail(testUser, ""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid application URL");
        }

        @Test
        @DisplayName("sendRegistrationVerificationEmail - rejects javascript URL")
        void sendRegistrationVerificationEmail_rejectsJavascriptUrl() {
            // When/Then
            assertThatThrownBy(() -> userEmailService.sendRegistrationVerificationEmail(testUser, "javascript:alert('xss')"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid application URL");
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

    @Nested
    @DisplayName("Admin Password Reset Tests")
    class AdminPasswordResetTests {

        @BeforeEach
        void setUpAdmin() {
            mockSecurityContext(adminUser);
        }

        @Test
        @DisplayName("initiateAdminPasswordReset - sends email and invalidates sessions when requested")
        void initiateAdminPasswordReset_sendsEmailAndInvalidatesSessions() {
            // Given
            when(sessionInvalidationService.invalidateUserSessions(testUser)).thenReturn(3);

            // When
            int invalidatedCount = userEmailService.initiateAdminPasswordReset(testUser, appUrl, true);

            // Then
            assertThat(invalidatedCount).isEqualTo(3);

            // Verify sessions were invalidated
            verify(sessionInvalidationService).invalidateUserSessions(testUser);

            // Verify password reset token was created
            ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
            verify(passwordTokenRepository).save(tokenCaptor.capture());
            PasswordResetToken savedToken = tokenCaptor.getValue();
            assertThat(savedToken.getUser()).isEqualTo(testUser);

            // Verify audit event was published with admin info from SecurityContext
            ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(eventPublisher).publishEvent(auditCaptor.capture());
            AuditEvent auditEvent = auditCaptor.getValue();
            assertThat(auditEvent.getAction()).isEqualTo("adminInitiatedPasswordReset");
            assertThat(auditEvent.getActionStatus()).isEqualTo("Success");
            assertThat(auditEvent.getMessage()).contains(adminUser.getEmail());
            // Audit extraData is now JSON format
            assertThat(auditEvent.getExtraData()).contains("\"adminIdentifier\":\"" + adminUser.getEmail() + "\"");
            assertThat(auditEvent.getExtraData()).contains("\"sessionsInvalidated\":3");

            // Verify email was sent
            verify(mailService).sendTemplateMessage(
                    eq(testUser.getEmail()),
                    eq("Password Reset"),
                    any(),
                    eq("mail/forgot-password-token.html")
            );
        }

        @Test
        @DisplayName("initiateAdminPasswordReset - skips session invalidation when not requested")
        void initiateAdminPasswordReset_skipsSessionInvalidationWhenNotRequested() {
            // When
            int invalidatedCount = userEmailService.initiateAdminPasswordReset(testUser, appUrl, false);

            // Then
            assertThat(invalidatedCount).isEqualTo(0);

            // Verify sessions were NOT invalidated
            verify(sessionInvalidationService, never()).invalidateUserSessions(any());

            // Verify email was still sent
            verify(mailService).sendTemplateMessage(
                    eq(testUser.getEmail()),
                    eq("Password Reset"),
                    any(),
                    eq("mail/forgot-password-token.html")
            );

            // Verify audit event shows 0 sessions invalidated (JSON format)
            ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(eventPublisher).publishEvent(auditCaptor.capture());
            AuditEvent auditEvent = auditCaptor.getValue();
            assertThat(auditEvent.getExtraData()).contains("\"sessionsInvalidated\":0");
        }

        @Test
        @DisplayName("initiateAdminPasswordReset - creates token and sends email correctly")
        void initiateAdminPasswordReset_createsTokenAndSendsEmail() {
            // Given
            when(sessionInvalidationService.invalidateUserSessions(testUser)).thenReturn(0);

            // When
            userEmailService.initiateAdminPasswordReset(testUser, appUrl, true);

            // Then
            ArgumentCaptor<Map<String, Object>> variablesCaptor = ArgumentCaptor.forClass(Map.class);
            verify(mailService).sendTemplateMessage(
                    eq(testUser.getEmail()),
                    eq("Password Reset"),
                    variablesCaptor.capture(),
                    eq("mail/forgot-password-token.html")
            );

            Map<String, Object> variables = variablesCaptor.getValue();
            assertThat(variables).containsKey("token");
            assertThat(variables.get("appUrl")).isEqualTo(appUrl);
            assertThat(variables.get("confirmationUrl")).asString()
                    .startsWith(appUrl + "/user/changePassword?token=");
        }

        @Test
        @DisplayName("initiateAdminPasswordReset - includes correlation ID in audit extraData as JSON")
        void initiateAdminPasswordReset_includesCorrelationIdInAuditExtraData() {
            // When
            userEmailService.initiateAdminPasswordReset(testUser, appUrl, false);

            // Then
            ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(eventPublisher).publishEvent(auditCaptor.capture());
            AuditEvent auditEvent = auditCaptor.getValue();
            // Audit extraData is now JSON format
            assertThat(auditEvent.getExtraData()).contains("\"correlationId\":\"");
            // Verify correlation ID is a UUID format within JSON
            assertThat(auditEvent.getExtraData()).matches(".*\"correlationId\":\"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\".*");
        }

        @Test
        @DisplayName("initiateAdminPasswordReset - gets admin identifier from SecurityContext")
        void initiateAdminPasswordReset_getsAdminIdentifierFromSecurityContext() {
            // When
            userEmailService.initiateAdminPasswordReset(testUser, appUrl, false);

            // Then
            ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(eventPublisher).publishEvent(auditCaptor.capture());
            AuditEvent auditEvent = auditCaptor.getValue();
            // Admin identifier should be from SecurityContext, not a parameter (JSON format)
            assertThat(auditEvent.getMessage()).contains(adminUser.getEmail());
            assertThat(auditEvent.getExtraData()).contains("\"adminIdentifier\":\"" + adminUser.getEmail() + "\"");
        }

        @Test
        @DisplayName("initiateAdminPasswordReset - returns UNKNOWN_ADMIN when not authenticated")
        void initiateAdminPasswordReset_returnsUnknownAdminWhenNotAuthenticated() {
            // Given - clear the security context
            SecurityContextHolder.clearContext();

            // When
            userEmailService.initiateAdminPasswordReset(testUser, appUrl, false);

            // Then
            ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(eventPublisher).publishEvent(auditCaptor.capture());
            AuditEvent auditEvent = auditCaptor.getValue();
            assertThat(auditEvent.getExtraData()).contains("\"adminIdentifier\":\"UNKNOWN_ADMIN\"");
        }
    }

    @Nested
    @DisplayName("URL Validation Tests")
    class UrlValidationTests {

        @BeforeEach
        void setUpAdmin() {
            mockSecurityContext(adminUser);
        }

        @Test
        @DisplayName("initiateAdminPasswordReset - accepts valid HTTPS URL")
        void initiateAdminPasswordReset_acceptsValidHttpsUrl() {
            // When/Then - no exception should be thrown
            userEmailService.initiateAdminPasswordReset(testUser, "https://example.com", false);

            verify(mailService).sendTemplateMessage(
                    eq(testUser.getEmail()),
                    eq("Password Reset"),
                    any(),
                    eq("mail/forgot-password-token.html")
            );
        }

        @Test
        @DisplayName("initiateAdminPasswordReset - accepts valid HTTP URL")
        void initiateAdminPasswordReset_acceptsValidHttpUrl() {
            // When/Then - no exception should be thrown
            userEmailService.initiateAdminPasswordReset(testUser, "http://localhost:8080", false);

            verify(mailService).sendTemplateMessage(
                    eq(testUser.getEmail()),
                    eq("Password Reset"),
                    any(),
                    eq("mail/forgot-password-token.html")
            );
        }

        @Test
        @DisplayName("initiateAdminPasswordReset - rejects javascript: URL")
        void initiateAdminPasswordReset_rejectsJavascriptUrl() {
            // When/Then
            assertThatThrownBy(() -> userEmailService.initiateAdminPasswordReset(testUser, "javascript:alert('xss')", false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid application URL");
        }

        @Test
        @DisplayName("initiateAdminPasswordReset - rejects data: URL")
        void initiateAdminPasswordReset_rejectsDataUrl() {
            // When/Then
            assertThatThrownBy(() -> userEmailService.initiateAdminPasswordReset(testUser, "data:text/html,<script>alert('xss')</script>", false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid application URL");
        }

        @Test
        @DisplayName("initiateAdminPasswordReset - rejects null URL")
        void initiateAdminPasswordReset_rejectsNullUrl() {
            // When/Then
            assertThatThrownBy(() -> userEmailService.initiateAdminPasswordReset(testUser, null, false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid application URL");
        }

        @Test
        @DisplayName("initiateAdminPasswordReset - rejects blank URL")
        void initiateAdminPasswordReset_rejectsBlankUrl() {
            // When/Then
            assertThatThrownBy(() -> userEmailService.initiateAdminPasswordReset(testUser, "   ", false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid application URL");
        }

        @Test
        @DisplayName("initiateAdminPasswordReset - rejects URL without host")
        void initiateAdminPasswordReset_rejectsUrlWithoutHost() {
            // When/Then
            assertThatThrownBy(() -> userEmailService.initiateAdminPasswordReset(testUser, "http://", false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid application URL");
        }
    }

    @Nested
    @DisplayName("PreAuthorize Annotation Tests")
    class PreAuthorizeAnnotationTests {

        @Test
        @DisplayName("initiateAdminPasswordReset(User, String, boolean) has @PreAuthorize annotation")
        void initiateAdminPasswordReset_hasPreAuthorizeAnnotation() throws NoSuchMethodException {
            // Given
            Method method = UserEmailService.class.getMethod("initiateAdminPasswordReset", User.class, String.class, boolean.class);

            // When
            PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);

            // Then
            assertThat(annotation).isNotNull();
            assertThat(annotation.value()).isEqualTo("hasRole('ADMIN')");
        }

        @Test
        @DisplayName("initiateAdminPasswordReset(User) has @PreAuthorize annotation")
        void initiateAdminPasswordReset_withUserOnly_hasPreAuthorizeAnnotation() throws NoSuchMethodException {
            // Given
            Method method = UserEmailService.class.getMethod("initiateAdminPasswordReset", User.class);

            // When
            PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);

            // Then
            assertThat(annotation).isNotNull();
            assertThat(annotation.value()).isEqualTo("hasRole('ADMIN')");
        }

        @Test
        @DisplayName("deprecated initiateAdminPasswordReset has @PreAuthorize annotation")
        void initiateAdminPasswordReset_deprecated_hasPreAuthorizeAnnotation() throws NoSuchMethodException {
            // Given - deprecated method with adminIdentifier parameter
            Method method = UserEmailService.class.getMethod("initiateAdminPasswordReset", User.class, String.class, String.class, boolean.class);

            // When
            PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);

            // Then
            assertThat(annotation).isNotNull();
            assertThat(annotation.value()).isEqualTo("hasRole('ADMIN')");
        }
    }

    @Nested
    @DisplayName("Deprecated Method Tests")
    class DeprecatedMethodTests {

        @BeforeEach
        void setUpAdmin() {
            mockSecurityContext(adminUser);
        }

        @Test
        @DisplayName("deprecated method with adminIdentifier still works but ignores parameter")
        @SuppressWarnings("deprecation")
        void deprecatedMethod_stillWorksButIgnoresAdminIdentifier() {
            // Given
            String ignoredAdminIdentifier = "ignored@example.com";

            // When
            userEmailService.initiateAdminPasswordReset(testUser, appUrl, ignoredAdminIdentifier, false);

            // Then - admin identifier should be from SecurityContext, not the parameter (JSON format)
            ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(eventPublisher).publishEvent(auditCaptor.capture());
            AuditEvent auditEvent = auditCaptor.getValue();
            // Should use admin from SecurityContext, not the ignored parameter
            assertThat(auditEvent.getExtraData()).contains("\"adminIdentifier\":\"" + adminUser.getEmail() + "\"");
            assertThat(auditEvent.getExtraData()).doesNotContain(ignoredAdminIdentifier);
        }

        @Test
        @DisplayName("deprecated method is marked with @Deprecated(forRemoval = true)")
        void deprecatedMethod_isMarkedForRemoval() throws NoSuchMethodException {
            // Given
            Method method = UserEmailService.class.getMethod("initiateAdminPasswordReset", User.class, String.class, String.class, boolean.class);

            // When
            Deprecated annotation = method.getAnnotation(Deprecated.class);

            // Then
            assertThat(annotation).isNotNull();
            assertThat(annotation.forRemoval()).isTrue();
        }
    }
}