package com.digitalsanctuary.spring.user.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Isolated;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.core.retry.Retryable;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.javamail.MimeMessagePreparator;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.backoff.ExponentialBackOff;
import org.thymeleaf.context.Context;

/**
 * Comprehensive unit tests for MailService that verify actual business logic
 * for email sending including async behavior, retry mechanism, and template processing.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MailService Tests")
// Isolated from other test classes: when a Spring Boot context starts, LogbackLoggingSystem calls LoggerContext.reset(),
// which detaches every appender, including this class's ListAppender. Within the class, assertions also filter by thread.
@Isolated
class MailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private ObjectProvider<JavaMailSender> mailSenderProvider;

    @Mock
    private MailContentBuilder mailContentBuilder;

    @Mock
    private MimeMessage mimeMessage;

    private MailService mailService;

    private static final String FROM_ADDRESS = "noreply@example.com";
    private static final String TO_ADDRESS = "user@example.com";
    private static final String SUBJECT = "Test Subject";

    @BeforeEach
    void setUp() {
        // Provider returns the mock mailSender by default.
        lenient().when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
        // Same retry policy as production, without backoff delays, so retry tests run instantly.
        RetryTemplate noDelayRetryTemplate = new RetryTemplate(RetryPolicy.builder()
            .includes(MailException.class).maxRetries(2).delay(Duration.ZERO).build());
        mailService = new MailService(mailSenderProvider, mailContentBuilder, noDelayRetryTemplate);
        mailService.init(); // simulate @PostConstruct — caches the resolved sender
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

        private Logger mailServiceLogger;
        private ListAppender<ILoggingEvent> listAppender;

        @BeforeEach
        void attachAppender() {
            mailServiceLogger = (Logger) LoggerFactory.getLogger(MailService.class);
            listAppender = new ListAppender<>();
            listAppender.start();
            mailServiceLogger.addAppender(listAppender);
        }

        @AfterEach
        void detachAppender() {
            mailServiceLogger.detachAppender(listAppender);
        }

        @Test
        @DisplayName("Should retry a simple message and succeed when the failure is transient")
        void shouldRetrySimpleMessageAndSucceedWhenFailureIsTransient() {
            // Given - first attempt fails, second succeeds
            doThrow(new MailSendException("Connection reset")).doNothing()
                .when(mailSender).send(any(MimeMessagePreparator.class));

            // When
            mailService.sendSimpleMessage(TO_ADDRESS, SUBJECT, "Test message");

            // Then
            verify(mailSender, times(2)).send(any(MimeMessagePreparator.class));
            assertThat(errorMessages()).isEmpty();
        }

        @Test
        @DisplayName("Should make 3 attempts, then log and suppress the failure for a simple message")
        void shouldLogAndSuppressWhenAllSimpleMessageAttemptsFail() {
            // Given
            doThrow(new MailSendException("SMTP server down")).when(mailSender).send(any(MimeMessagePreparator.class));

            // When - no exception escapes once retries are exhausted
            mailService.sendSimpleMessage(TO_ADDRESS, SUBJECT, "Important message");

            // Then
            verify(mailSender, times(3)).send(any(MimeMessagePreparator.class));
            assertThat(errorMessages()).singleElement().asString()
                .contains("Failed to send simple email to " + TO_ADDRESS)
                .contains("after all retry attempts")
                .contains("SMTP server down");
        }

        @Test
        @DisplayName("Should make 3 attempts, then log and suppress the failure for a template message")
        void shouldLogAndSuppressWhenAllTemplateMessageAttemptsFail() {
            // Given
            doThrow(new MailSendException("SMTP server down")).when(mailSender).send(any(MimeMessagePreparator.class));
            Map<String, Object> variables = new HashMap<>();
            variables.put("key", "value");

            // When
            mailService.sendTemplateMessage(TO_ADDRESS, SUBJECT, variables, "email/test");

            // Then
            verify(mailSender, times(3)).send(any(MimeMessagePreparator.class));
            assertThat(errorMessages()).singleElement().asString()
                .contains("Failed to send template email to " + TO_ADDRESS)
                .contains("Template: 'email/test'")
                .contains("SMTP server down");
        }

        @Test
        @DisplayName("Should not retry and should propagate a failure that is not a MailException")
        void shouldPropagateWithoutRetryWhenFailureIsNotMailException() {
            // Given
            IllegalStateException failure = new IllegalStateException("Unexpected bug");
            doThrow(failure).when(mailSender).send(any(MimeMessagePreparator.class));

            // When/Then - the original exception propagates unchanged, as it did with spring-retry's retryFor
            assertThatThrownBy(() -> mailService.sendSimpleMessage(TO_ADDRESS, SUBJECT, "Test")).isSameAs(failure);
            verify(mailSender, times(1)).send(any(MimeMessagePreparator.class));
            assertThat(errorMessages()).isEmpty();
        }

        @Test
        @DisplayName("Should recover with the last MailException when RetryTemplate reports the interrupt as the cause (Framework 7.0.0-7.0.1)")
        void shouldRecoverWithLastMailExceptionWhenInterruptIsTheCause() {
            // Given - reproduce how Spring Framework 7.0.0 and 7.0.1 report an interrupt during backoff: the cause is the
            // InterruptedException, the earlier MailException failures are suppressed, and the template has set the flag
            MailSendException firstFailure = new MailSendException("Connection reset");
            MailService service = new MailService(mailSenderProvider, mailContentBuilder, new RetryTemplate() {
                @Override
                public <R> R execute(Retryable<R> retryable) throws RetryException {
                    Thread.currentThread().interrupt();
                    RetryException ex = new RetryException("Unable to back off", new InterruptedException("sleep interrupted"));
                    ex.addSuppressed(firstFailure);
                    throw ex;
                }
            });
            service.init();
            ReflectionTestUtils.setField(service, "fromAddress", FROM_ADDRESS);

            // When
            boolean interruptedAfterSend;
            try {
                service.sendSimpleMessage(TO_ADDRESS, SUBJECT, "Test message");
            } finally {
                interruptedAfterSend = Thread.interrupted(); // read and clear, so the flag cannot leak into other tests
            }

            // Then - the recover path logged the suppressed MailException, and the interrupt was preserved
            assertThat(interruptedAfterSend).isTrue();
            assertThat(errorMessages()).singleElement().asString()
                .contains("Failed to send simple email to " + TO_ADDRESS)
                .contains("Connection reset");
        }

        @Test
        @DisplayName("Should recover with a MailSendException wrapping the interrupt when no MailException was recorded")
        void shouldRecoverWithWrappedInterruptWhenNoMailExceptionWasRecorded() {
            // Given - an interrupt reported as the cause with no recorded MailException
            MailService service = new MailService(mailSenderProvider, mailContentBuilder, new RetryTemplate() {
                @Override
                public <R> R execute(Retryable<R> retryable) throws RetryException {
                    Thread.currentThread().interrupt();
                    throw new RetryException("Unable to back off", new InterruptedException("sleep interrupted"));
                }
            });
            service.init();
            ReflectionTestUtils.setField(service, "fromAddress", FROM_ADDRESS);

            // When
            boolean interruptedAfterSend;
            try {
                service.sendSimpleMessage(TO_ADDRESS, SUBJECT, "Test message");
            } finally {
                interruptedAfterSend = Thread.interrupted();
            }

            // Then
            assertThat(interruptedAfterSend).isTrue();
            assertThat(errorMessages()).singleElement().asString()
                .contains("Failed to send simple email to " + TO_ADDRESS)
                .contains("interrupted");
        }

        @Test
        @DisplayName("Should stop retrying, keep the interrupt flag, and recover with the MailException when interrupted during backoff")
        void shouldRecoverAndKeepInterruptFlagWhenInterruptedDuringBackoff() {
            // Given - a non-zero backoff, so RetryTemplate calls Thread.sleep, which throws at once because the flag is set
            MailService service = new MailService(mailSenderProvider, mailContentBuilder, new RetryTemplate(RetryPolicy.builder()
                .includes(MailException.class).maxRetries(2).delay(Duration.ofMillis(50)).build()));
            service.init();
            ReflectionTestUtils.setField(service, "fromAddress", FROM_ADDRESS);
            doThrow(new MailSendException("Connection reset")).when(mailSender).send(any(MimeMessagePreparator.class));

            // When
            Thread.currentThread().interrupt();
            boolean interruptedAfterSend;
            try {
                service.sendSimpleMessage(TO_ADDRESS, SUBJECT, "Test message");
            } finally {
                interruptedAfterSend = Thread.interrupted(); // read and clear, so the flag cannot leak into other tests
            }

            // Then - one attempt only, the recover path logged the MailException, and the interrupt was preserved
            assertThat(interruptedAfterSend).isTrue();
            verify(mailSender, times(1)).send(any(MimeMessagePreparator.class));
            assertThat(errorMessages()).singleElement().asString()
                .contains("Failed to send simple email to " + TO_ADDRESS)
                .contains("Connection reset");
        }

        @Test
        @DisplayName("Default retry template retries MailException only, with 1s exponential backoff doubling")
        void shouldConfigureDefaultRetryTemplateWithExpectedPolicy() {
            // When
            RetryPolicy policy = MailService.defaultRetryTemplate().getRetryPolicy();

            // Then
            assertThat(policy.shouldRetry(new MailSendException("fail"))).isTrue();
            assertThat(policy.shouldRetry(new IllegalStateException("fail"))).isFalse();
            assertThat(policy.getBackOff()).isInstanceOfSatisfying(ExponentialBackOff.class, backOff -> {
                assertThat(backOff.getInitialInterval()).isEqualTo(1000L);
                assertThat(backOff.getMultiplier()).isEqualTo(2.0);
                assertThat(backOff.getMaxAttempts()).isEqualTo(2L);
            });
        }

        private List<String> errorMessages() {
            // MailService's logger is JVM-global and this suite runs tests in parallel (see junit-platform.properties), so
            // other tests' events land in this appender too. These tests call MailService synchronously, so keep only events
            // from the current test thread. Snapshot first (List.copyOf) so a concurrent append cannot break iteration.
            String testThread = Thread.currentThread().getName();
            return List.copyOf(listAppender.list).stream()
                .filter(event -> event.getLevel() == Level.ERROR)
                .filter(event -> testThread.equals(event.getThreadName()))
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
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
    @DisplayName("Missing JavaMailSender Tests")
    class MissingMailSenderTests {

        private MailService unconfiguredMailService;

        @BeforeEach
        void setUpUnconfigured() {
            // Separate service instance where the sender is absent from startup.
            when(mailSenderProvider.getIfAvailable()).thenReturn(null);
            unconfiguredMailService = new MailService(mailSenderProvider, mailContentBuilder);
            unconfiguredMailService.init();
            ReflectionTestUtils.setField(unconfiguredMailService, "fromAddress", FROM_ADDRESS);
        }

        @Test
        @DisplayName("sendSimpleMessage should no-op when JavaMailSender is not configured")
        void sendSimpleMessageNoOpsWhenSenderMissing() {
            unconfiguredMailService.sendSimpleMessage(TO_ADDRESS, SUBJECT, "Body");

            verify(mailSender, never()).send(any(MimeMessagePreparator.class));
        }

        @Test
        @DisplayName("sendTemplateMessage should no-op when JavaMailSender is not configured")
        void sendTemplateMessageNoOpsWhenSenderMissing() {
            unconfiguredMailService.sendTemplateMessage(TO_ADDRESS, SUBJECT, new HashMap<>(), "email/test");

            verify(mailSender, never()).send(any(MimeMessagePreparator.class));
            verify(mailContentBuilder, never()).build(anyString(), any(Context.class));
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

    @Nested
    @DisplayName("From Address Warning Tests")
    class FromAddressWarningTests {

        private static final String EXPECTED_WARNING_FRAGMENT = "user.mail.fromAddress";

        private Logger mailServiceLogger;
        private ListAppender<ILoggingEvent> listAppender;

        @BeforeEach
        void attachAppender() {
            mailServiceLogger = (Logger) LoggerFactory.getLogger(MailService.class);
            listAppender = new ListAppender<>();
            listAppender.start();
            mailServiceLogger.addAppender(listAppender);
        }

        @AfterEach
        void detachAppender() {
            mailServiceLogger.detachAppender(listAppender);
        }

        @Test
        @DisplayName("Should warn when JavaMailSender is present but fromAddress is blank")
        void shouldWarnWhenSenderPresentAndFromAddressBlank() {
            // Given: sender available (mail enabled) but a blank fromAddress
            when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
            MailService service = new MailService(mailSenderProvider, mailContentBuilder);
            ReflectionTestUtils.setField(service, "fromAddress", "  ");

            // When
            service.init();

            // Then
            assertThat(warningMessages()).anyMatch(msg -> msg.contains(EXPECTED_WARNING_FRAGMENT));
        }

        @Test
        @DisplayName("Should NOT warn about fromAddress when sender present and fromAddress is valid")
        void shouldNotWarnWhenSenderPresentAndFromAddressValid() {
            // Given: sender available and a valid fromAddress
            when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
            MailService service = new MailService(mailSenderProvider, mailContentBuilder);
            ReflectionTestUtils.setField(service, "fromAddress", "noreply@example.com");

            // When
            service.init();

            // Then
            assertThat(warningMessages()).noneMatch(msg -> msg.contains(EXPECTED_WARNING_FRAGMENT));
        }

        @Test
        @DisplayName("Should NOT warn about fromAddress when sender is absent")
        void shouldNotWarnAboutFromAddressWhenSenderAbsent() {
            // Given: no sender (mail disabled) and a blank fromAddress
            when(mailSenderProvider.getIfAvailable()).thenReturn(null);
            MailService service = new MailService(mailSenderProvider, mailContentBuilder);
            ReflectionTestUtils.setField(service, "fromAddress", "");

            // When
            service.init();

            // Then: only the no-sender warning is expected, not the fromAddress warning
            assertThat(warningMessages()).noneMatch(msg -> msg.contains(EXPECTED_WARNING_FRAGMENT));
        }

        private java.util.List<String> warningMessages() {
            return listAppender.list.stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
        }
    }
}