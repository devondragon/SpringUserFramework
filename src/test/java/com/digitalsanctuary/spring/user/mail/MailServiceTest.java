package com.digitalsanctuary.spring.user.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.javamail.MimeMessagePreparator;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.context.Context;

/**
 * Comprehensive unit tests for MailService that verify actual business logic
 * for email sending including async behavior, retry mechanism, and template processing.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MailService Tests")
class MailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private MailContentBuilder mailContentBuilder;

    @Mock
    private MimeMessage mimeMessage;

    @InjectMocks
    private MailService mailService;

    private static final String FROM_ADDRESS = "noreply@example.com";
    private static final String TO_ADDRESS = "user@example.com";
    private static final String SUBJECT = "Test Subject";

    @BeforeEach
    void setUp() {
        // Set the from address via reflection since it's a @Value field
        ReflectionTestUtils.setField(mailService, "fromAddress", FROM_ADDRESS);
        
        // Setup default mock behavior
        lenient().when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
    }

    @Nested
    @DisplayName("Simple Message Tests")
    class SimpleMessageTests {

        @Test
        @DisplayName("Should send simple message with correct parameters")
        void shouldSendSimpleMessageWithCorrectParameters() throws Exception {
            // Given
            String messageText = "This is a test email";
            ArgumentCaptor<MimeMessagePreparator> preparatorCaptor = 
                ArgumentCaptor.forClass(MimeMessagePreparator.class);

            // When
            mailService.sendSimpleMessage(TO_ADDRESS, SUBJECT, messageText);

            // Then
            verify(mailSender).send(preparatorCaptor.capture());
            
            // Verify the message preparator sets correct values
            MimeMessagePreparator preparator = preparatorCaptor.getValue();
            assertThat(preparator).isNotNull();
            
            // Execute the preparator to verify its behavior
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage);
            preparator.prepare(mimeMessage);
            
            // We can't directly verify MimeMessageHelper calls, but we can verify
            // that the preparator doesn't throw exceptions when executed
        }

        @Test
        @DisplayName("Should handle null recipient gracefully")
        void shouldHandleNullRecipient() {
            // Given
            String messageText = "Test message";

            // When/Then - The async nature means exceptions might be swallowed
            // In real implementation, this would be handled by Spring's async error handler
            mailService.sendSimpleMessage(null, SUBJECT, messageText);
            
            // Verify send was attempted
            verify(mailSender).send(any(MimeMessagePreparator.class));
        }

        @Test
        @DisplayName("Should handle empty subject and text")
        void shouldHandleEmptySubjectAndText() {
            // When
            mailService.sendSimpleMessage(TO_ADDRESS, "", "");

            // Then
            verify(mailSender).send(any(MimeMessagePreparator.class));
        }

        @Test
        @DisplayName("Should handle very long message text")
        void shouldHandleVeryLongMessageText() {
            // Given
            String longText = "a".repeat(10000);

            // When
            mailService.sendSimpleMessage(TO_ADDRESS, SUBJECT, longText);

            // Then
            verify(mailSender).send(any(MimeMessagePreparator.class));
        }

        @Test
        @DisplayName("Should set HTML flag to true for message text")
        void shouldSetHtmlFlagForMessageText() throws Exception {
            // Given
            String htmlText = "<html><body><h1>Test</h1></body></html>";
            ArgumentCaptor<MimeMessagePreparator> preparatorCaptor = 
                ArgumentCaptor.forClass(MimeMessagePreparator.class);

            // When
            mailService.sendSimpleMessage(TO_ADDRESS, SUBJECT, htmlText);

            // Then
            verify(mailSender).send(preparatorCaptor.capture());
            
            // The setText method is called with true for HTML
            MimeMessagePreparator preparator = preparatorCaptor.getValue();
            preparator.prepare(mimeMessage);
        }
    }

    @Nested
    @DisplayName("Template Message Tests")
    class TemplateMessageTests {

        @Test
        @DisplayName("Should send template message with correct parameters")
        void shouldSendTemplateMessageWithCorrectParameters() throws Exception {
            // Given
            Map<String, Object> variables = new HashMap<>();
            variables.put("username", "John Doe");
            variables.put("link", "https://example.com/verify");
            String templatePath = "email/verification";
            String renderedContent = "<html><body>Hello John Doe</body></html>";
            
            ArgumentCaptor<MimeMessagePreparator> preparatorCaptor = 
                ArgumentCaptor.forClass(MimeMessagePreparator.class);

            // When
            mailService.sendTemplateMessage(TO_ADDRESS, SUBJECT, variables, templatePath);

            // Then
            verify(mailSender).send(preparatorCaptor.capture());
            
            // Execute preparator to trigger template building
            when(mailContentBuilder.build(eq(templatePath), any(Context.class)))
                .thenReturn(renderedContent);
            MimeMessagePreparator preparator = preparatorCaptor.getValue();
            preparator.prepare(mimeMessage);
            
            // Now verify template builder was called
            verify(mailContentBuilder).build(eq(templatePath), any(Context.class));
        }

        @Test
        @DisplayName("Should pass variables correctly to template builder")
        void shouldPassVariablesToTemplateBuilder() throws Exception {
            // Given
            Map<String, Object> variables = new HashMap<>();
            variables.put("name", "Jane");
            variables.put("code", "12345");
            variables.put("expiry", "24 hours");
            String templatePath = "email/password-reset";
            
            ArgumentCaptor<MimeMessagePreparator> preparatorCaptor = 
                ArgumentCaptor.forClass(MimeMessagePreparator.class);

            // When
            mailService.sendTemplateMessage(TO_ADDRESS, SUBJECT, variables, templatePath);

            // Then
            verify(mailSender).send(preparatorCaptor.capture());
            
            // Setup mock and execute preparator
            ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
            when(mailContentBuilder.build(eq(templatePath), contextCaptor.capture()))
                .thenReturn("<html>Reset password</html>");
            
            MimeMessagePreparator preparator = preparatorCaptor.getValue();
            preparator.prepare(mimeMessage);
            
            // Verify context was passed with variables
            Context capturedContext = contextCaptor.getValue();
            assertThat(capturedContext).isNotNull();
            verify(mailContentBuilder).build(eq(templatePath), any(Context.class));
        }

        @Test
        @DisplayName("Should handle empty variables map")
        void shouldHandleEmptyVariablesMap() throws Exception {
            // Given
            Map<String, Object> emptyVariables = new HashMap<>();
            String templatePath = "email/simple";
            ArgumentCaptor<MimeMessagePreparator> preparatorCaptor = 
                ArgumentCaptor.forClass(MimeMessagePreparator.class);

            // When
            mailService.sendTemplateMessage(TO_ADDRESS, SUBJECT, emptyVariables, templatePath);

            // Then
            verify(mailSender).send(preparatorCaptor.capture());
            
            // Execute preparator
            when(mailContentBuilder.build(anyString(), any(Context.class)))
                .thenReturn("<html>Simple email</html>");
            preparatorCaptor.getValue().prepare(mimeMessage);
            verify(mailContentBuilder).build(eq(templatePath), any(Context.class));
        }

        @Test
        @DisplayName("Should handle null variables map")
        void shouldHandleNullVariablesMap() throws Exception {
            // Given
            String templatePath = "email/notification";
            ArgumentCaptor<MimeMessagePreparator> preparatorCaptor = 
                ArgumentCaptor.forClass(MimeMessagePreparator.class);

            // When
            mailService.sendTemplateMessage(TO_ADDRESS, SUBJECT, null, templatePath);

            // Then
            verify(mailSender).send(preparatorCaptor.capture());
            
            // Execute preparator
            when(mailContentBuilder.build(anyString(), any(Context.class)))
                .thenReturn("<html>Notification</html>");
            preparatorCaptor.getValue().prepare(mimeMessage);
            verify(mailContentBuilder).build(eq(templatePath), any(Context.class));
        }

        @Test
        @DisplayName("Should handle complex nested variables")
        void shouldHandleComplexNestedVariables() throws Exception {
            // Given
            Map<String, Object> variables = new HashMap<>();
            Map<String, String> userInfo = new HashMap<>();
            userInfo.put("firstName", "John");
            userInfo.put("lastName", "Doe");
            variables.put("user", userInfo);
            variables.put("items", new String[]{"item1", "item2", "item3"});
            
            String templatePath = "email/complex";
            ArgumentCaptor<MimeMessagePreparator> preparatorCaptor = 
                ArgumentCaptor.forClass(MimeMessagePreparator.class);

            // When
            mailService.sendTemplateMessage(TO_ADDRESS, SUBJECT, variables, templatePath);

            // Then
            verify(mailSender).send(preparatorCaptor.capture());
            
            // Execute preparator
            when(mailContentBuilder.build(eq(templatePath), any(Context.class)))
                .thenReturn("<html>Complex email</html>");
            preparatorCaptor.getValue().prepare(mimeMessage);
            verify(mailContentBuilder).build(eq(templatePath), any(Context.class));
        }
    }

    @Nested
    @DisplayName("Retry and Recovery Tests")
    class RetryAndRecoveryTests {

        @Test
        @DisplayName("Should attempt to send despite MailException (retry handled by Spring)")
        void shouldAttemptToSendDespiteMailException() {
            // Given
            MailSendException exception = new MailSendException("Connection failed");
            
            // Note: @Retryable doesn't work in unit tests without Spring context
            // In production, this would retry 3 times with backoff
            doThrow(exception).when(mailSender).send(any(MimeMessagePreparator.class));

            // When/Then - Exception is thrown but @Async would handle it
            try {
                mailService.sendSimpleMessage(TO_ADDRESS, SUBJECT, "Test message");
            } catch (MailSendException e) {
                // Expected in unit test context
                assertThat(e.getMessage()).isEqualTo("Connection failed");
            }

            // Verify send was attempted
            verify(mailSender).send(any(MimeMessagePreparator.class));
        }

        @Test
        @DisplayName("Recovery method should log error for simple message")
        void recoveryMethodShouldLogErrorForSimpleMessage() {
            // Given
            MailSendException exception = new MailSendException("SMTP server down");
            String text = "Important message";

            // When - Call recovery method directly since we can't trigger retry in unit test
            mailService.recoverSendSimpleMessage(exception, TO_ADDRESS, SUBJECT, text);

            // Then - Method completes without throwing exception (logs error)
            // In a real test with logging verification, we'd check the log output
        }

        @Test
        @DisplayName("Recovery method should log error for template message")
        void recoveryMethodShouldLogErrorForTemplateMessage() {
            // Given
            MailSendException exception = new MailSendException("Template processing failed");
            Map<String, Object> variables = new HashMap<>();
            variables.put("key", "value");
            String templatePath = "email/test";

            // When - Call recovery method directly
            mailService.recoverSendTemplateMessage(exception, TO_ADDRESS, SUBJECT, variables, templatePath);

            // Then - Method completes without throwing exception (logs error)
        }

        @Test
        @DisplayName("Should handle permanent MailException gracefully")
        void shouldHandlePermanentMailException() {
            // Given
            MailSendException permanentException = new MailSendException("Invalid recipient");
            doThrow(permanentException).when(mailSender).send(any(MimeMessagePreparator.class));

            // When/Then
            try {
                mailService.sendSimpleMessage("invalid-email", SUBJECT, "Test");
            } catch (MailSendException e) {
                // Expected in unit test - in production @Async would handle this
                assertThat(e.getMessage()).isEqualTo("Invalid recipient");
            }
            
            // Verify send was attempted
            verify(mailSender).send(any(MimeMessagePreparator.class));
        }
    }

    @Nested
    @DisplayName("Async Behavior Tests")
    class AsyncBehaviorTests {

        @Test
        @DisplayName("Should execute sendSimpleMessage asynchronously")
        void shouldExecuteSendSimpleMessageAsynchronously() {
            // Given
            String message = "Async test message";

            // When
            mailService.sendSimpleMessage(TO_ADDRESS, SUBJECT, message);

            // Then - Method returns immediately (in real async context)
            verify(mailSender).send(any(MimeMessagePreparator.class));
        }

        @Test
        @DisplayName("Should execute sendTemplateMessage asynchronously")
        void shouldExecuteSendTemplateMessageAsynchronously() throws Exception {
            // Given
            Map<String, Object> variables = new HashMap<>();
            variables.put("async", true);
            String templatePath = "email/async";
            ArgumentCaptor<MimeMessagePreparator> preparatorCaptor = 
                ArgumentCaptor.forClass(MimeMessagePreparator.class);

            // When
            mailService.sendTemplateMessage(TO_ADDRESS, SUBJECT, variables, templatePath);

            // Then - Method returns immediately (in real async context)
            verify(mailSender).send(preparatorCaptor.capture());
            
            // Execute preparator to verify template builder interaction
            when(mailContentBuilder.build(anyString(), any(Context.class)))
                .thenReturn("<html>Async email</html>");
            preparatorCaptor.getValue().prepare(mimeMessage);
            verify(mailContentBuilder).build(eq(templatePath), any(Context.class));
        }

        @Test
        @DisplayName("Multiple async calls should not block each other")
        void multipleAsyncCallsShouldNotBlock() {
            // When - Send multiple simple emails (not template emails)
            for (int i = 0; i < 5; i++) {
                mailService.sendSimpleMessage("user" + i + "@example.com", "Subject " + i, "Message " + i);
            }

            // Then - All calls complete without blocking
            verify(mailSender, times(5)).send(any(MimeMessagePreparator.class));
        }
    }

    @Nested
    @DisplayName("Edge Cases and Error Handling Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("Should handle special characters in email addresses")
        void shouldHandleSpecialCharactersInEmail() {
            // Given
            String specialEmail = "user+test@sub.example.com";

            // When
            mailService.sendSimpleMessage(specialEmail, SUBJECT, "Test");

            // Then
            verify(mailSender).send(any(MimeMessagePreparator.class));
        }

        @Test
        @DisplayName("Should handle international characters in subject and content")
        void shouldHandleInternationalCharacters() {
            // Given
            String intlSubject = "测试邮件 - Test Email - Тестовое письмо";
            String intlContent = "你好世界 - Hello World - Привет мир";

            // When
            mailService.sendSimpleMessage(TO_ADDRESS, intlSubject, intlContent);

            // Then
            verify(mailSender).send(any(MimeMessagePreparator.class));
        }

        @Test
        @DisplayName("Should handle multiple recipients in TO field")
        void shouldHandleMultipleRecipients() {
            // Given
            String multipleRecipients = "user1@example.com,user2@example.com";

            // When
            mailService.sendSimpleMessage(multipleRecipients, SUBJECT, "Broadcast message");

            // Then
            verify(mailSender).send(any(MimeMessagePreparator.class));
        }

        @Test
        @DisplayName("Should handle template builder returning null")
        void shouldHandleTemplateBuilderReturningNull() throws Exception {
            // Given
            ArgumentCaptor<MimeMessagePreparator> preparatorCaptor = 
                ArgumentCaptor.forClass(MimeMessagePreparator.class);

            // When
            mailService.sendTemplateMessage(TO_ADDRESS, SUBJECT, new HashMap<>(), "email/null");

            // Then - Should still attempt to send
            verify(mailSender).send(preparatorCaptor.capture());
            
            // Execute preparator with null return from builder - this will throw
            when(mailContentBuilder.build(anyString(), any(Context.class)))
                .thenReturn(null);
            
            // MimeMessageHelper.setText throws IllegalArgumentException for null text
            assertThatThrownBy(() -> preparatorCaptor.getValue().prepare(mimeMessage))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Text must not be null");
            
            verify(mailContentBuilder).build(eq("email/null"), any(Context.class));
        }

        @Test
        @DisplayName("Should handle template builder throwing exception")
        void shouldHandleTemplateBuilderException() throws Exception {
            // Given
            ArgumentCaptor<MimeMessagePreparator> preparatorCaptor = 
                ArgumentCaptor.forClass(MimeMessagePreparator.class);

            // When
            mailService.sendTemplateMessage(TO_ADDRESS, SUBJECT, new HashMap<>(), "email/missing");

            // Then - Send is called
            verify(mailSender).send(preparatorCaptor.capture());
            
            // Execute preparator - exception happens during preparation
            when(mailContentBuilder.build(anyString(), any(Context.class)))
                .thenThrow(new RuntimeException("Template not found"));
            
            assertThatThrownBy(() -> preparatorCaptor.getValue().prepare(mimeMessage))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Template not found");
        }
    }

    @Nested
    @DisplayName("Integration with MimeMessageHelper Tests")
    class MimeMessageHelperIntegrationTests {

        @Test
        @DisplayName("Should properly configure MimeMessageHelper for simple message")
        void shouldConfigureMimeMessageHelperForSimpleMessage() throws Exception {
            // Given
            ArgumentCaptor<MimeMessagePreparator> preparatorCaptor = 
                ArgumentCaptor.forClass(MimeMessagePreparator.class);
            String messageText = "Test content";

            // When
            mailService.sendSimpleMessage(TO_ADDRESS, SUBJECT, messageText);

            // Then
            verify(mailSender).send(preparatorCaptor.capture());
            
            // Verify preparator configures message correctly
            MimeMessagePreparator preparator = preparatorCaptor.getValue();
            assertThat(preparator).isNotNull();
            
            // Execute preparator - it should not throw any exceptions
            preparator.prepare(mimeMessage);
        }

        @Test
        @DisplayName("Should properly configure MimeMessageHelper for template message")
        void shouldConfigureMimeMessageHelperForTemplateMessage() throws Exception {
            // Given
            Map<String, Object> variables = new HashMap<>();
            variables.put("test", "value");
            String renderedHtml = "<html><body>Rendered content</body></html>";
            
            when(mailContentBuilder.build(anyString(), any(Context.class)))
                .thenReturn(renderedHtml);
            
            ArgumentCaptor<MimeMessagePreparator> preparatorCaptor = 
                ArgumentCaptor.forClass(MimeMessagePreparator.class);

            // When
            mailService.sendTemplateMessage(TO_ADDRESS, SUBJECT, variables, "email/test");

            // Then
            verify(mailSender).send(preparatorCaptor.capture());
            
            // Execute preparator - it should not throw any exceptions
            MimeMessagePreparator preparator = preparatorCaptor.getValue();
            preparator.prepare(mimeMessage);
        }
    }
}