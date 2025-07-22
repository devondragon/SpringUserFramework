package com.digitalsanctuary.spring.user.audit;

import static org.mockito.Mockito.*;

import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.test.builders.UserTestDataBuilder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditEventListener Tests")
class AuditEventListenerTest {

    @Mock
    private AuditConfig auditConfig;

    @Mock
    private AuditLogWriter auditLogWriter;

    @InjectMocks
    private AuditEventListener auditEventListener;

    private User testUser;
    private AuditEvent auditEvent;

    @BeforeEach
    void setUp() {
        testUser = UserTestDataBuilder.aUser()
                .withId(1L)
                .withEmail("test@example.com")
                .withFirstName("Test")
                .withLastName("User")
                .enabled()
                .build();
    }

    @Nested
    @DisplayName("Event Logging Tests")
    class EventLoggingTests {

        @Test
        @DisplayName("onApplicationEvent - logs event when logging is enabled")
        void onApplicationEvent_logsEventWhenEnabled() {
            // Given
            when(auditConfig.isLogEvents()).thenReturn(true);
            auditEvent = AuditEvent.builder()
                    .source(this)
                    .user(testUser)
                    .action("Login")
                    .actionStatus("Success")
                    .message("User logged in successfully")
                    .ipAddress("192.168.1.1")
                    .userAgent("Mozilla/5.0")
                    .sessionId("session123")
                    .build();

            // When
            auditEventListener.onApplicationEvent(auditEvent);

            // Then
            verify(auditLogWriter).writeLog(auditEvent);
        }

        @Test
        @DisplayName("onApplicationEvent - does not log when logging is disabled")
        void onApplicationEvent_doesNotLogWhenDisabled() {
            // Given
            when(auditConfig.isLogEvents()).thenReturn(false);
            auditEvent = AuditEvent.builder()
                    .source(this)
                    .user(testUser)
                    .action("Login")
                    .actionStatus("Success")
                    .build();

            // When
            auditEventListener.onApplicationEvent(auditEvent);

            // Then
            verify(auditLogWriter, never()).writeLog(any());
        }

        @Test
        @DisplayName("onApplicationEvent - handles null event gracefully")
        void onApplicationEvent_handlesNullEvent() {
            // Given
            when(auditConfig.isLogEvents()).thenReturn(true);

            // When
            auditEventListener.onApplicationEvent(null);

            // Then
            verify(auditLogWriter, never()).writeLog(any());
        }
    }

    @Nested
    @DisplayName("Different Event Type Tests")
    class DifferentEventTypeTests {

        @BeforeEach
        void setUp() {
            when(auditConfig.isLogEvents()).thenReturn(true);
        }

        @Test
        @DisplayName("Logs registration event")
        void logs_registrationEvent() {
            // Given
            auditEvent = AuditEvent.builder()
                    .source(this)
                    .user(testUser)
                    .action("Registration")
                    .actionStatus("Success")
                    .message("New user registered")
                    .build();

            // When
            auditEventListener.onApplicationEvent(auditEvent);

            // Then
            verify(auditLogWriter).writeLog(auditEvent);
        }

        @Test
        @DisplayName("Logs password reset event")
        void logs_passwordResetEvent() {
            // Given
            auditEvent = AuditEvent.builder()
                    .source(this)
                    .user(testUser)
                    .action("Password Reset")
                    .actionStatus("Success")
                    .message("Password reset requested")
                    .build();

            // When
            auditEventListener.onApplicationEvent(auditEvent);

            // Then
            verify(auditLogWriter).writeLog(auditEvent);
        }

        @Test
        @DisplayName("Logs failed login event")
        void logs_failedLoginEvent() {
            // Given
            auditEvent = AuditEvent.builder()
                    .source(this)
                    .user(null)
                    .action("Login")
                    .actionStatus("Failed")
                    .message("Invalid credentials")
                    .ipAddress("192.168.1.100")
                    .build();

            // When
            auditEventListener.onApplicationEvent(auditEvent);

            // Then
            verify(auditLogWriter).writeLog(auditEvent);
        }

        @Test
        @DisplayName("Logs account deletion event")
        void logs_accountDeletionEvent() {
            // Given
            auditEvent = AuditEvent.builder()
                    .source(this)
                    .user(testUser)
                    .action("Account Deletion")
                    .actionStatus("Success")
                    .message("User account deleted")
                    .build();

            // When
            auditEventListener.onApplicationEvent(auditEvent);

            // Then
            verify(auditLogWriter).writeLog(auditEvent);
        }
    }

    @Nested
    @DisplayName("Multiple Event Tests")
    class MultipleEventTests {

        @BeforeEach
        void setUp() {
            when(auditConfig.isLogEvents()).thenReturn(true);
        }

        @Test
        @DisplayName("Handles multiple events in sequence")
        void handles_multipleEventsInSequence() {
            // Given
            AuditEvent event1 = AuditEvent.builder()
                    .source(this)
                    .action("Login")
                    .actionStatus("Success")
                    .build();
            AuditEvent event2 = AuditEvent.builder()
                    .source(this)
                    .action("View Profile")
                    .actionStatus("Success")
                    .build();
            AuditEvent event3 = AuditEvent.builder()
                    .source(this)
                    .action("Logout")
                    .actionStatus("Success")
                    .build();

            // When
            auditEventListener.onApplicationEvent(event1);
            auditEventListener.onApplicationEvent(event2);
            auditEventListener.onApplicationEvent(event3);

            // Then
            verify(auditLogWriter).writeLog(event1);
            verify(auditLogWriter).writeLog(event2);
            verify(auditLogWriter).writeLog(event3);
        }

        @Test
        @DisplayName("Configuration change affects subsequent events")
        void configurationChange_affectsSubsequentEvents() {
            // Given
            AuditEvent event1 = AuditEvent.builder()
                    .source(this)
                    .action("Action 1")
                    .actionStatus("Success")
                    .build();
            AuditEvent event2 = AuditEvent.builder()
                    .source(this)
                    .action("Action 2")
                    .actionStatus("Success")
                    .build();

            // When
            when(auditConfig.isLogEvents()).thenReturn(true);
            auditEventListener.onApplicationEvent(event1);
            
            when(auditConfig.isLogEvents()).thenReturn(false);
            auditEventListener.onApplicationEvent(event2);

            // Then
            verify(auditLogWriter).writeLog(event1);
            verify(auditLogWriter, never()).writeLog(event2);
        }
    }

    @Nested
    @DisplayName("Event Content Tests")
    class EventContentTests {

        @BeforeEach
        void setUp() {
            when(auditConfig.isLogEvents()).thenReturn(true);
        }

        @Test
        @DisplayName("Logs event with minimal information")
        void logs_eventWithMinimalInfo() {
            // Given
            auditEvent = AuditEvent.builder()
                    .source(this)
                    .action("Action")
                    .actionStatus("Status")
                    .build();

            // When
            auditEventListener.onApplicationEvent(auditEvent);

            // Then
            verify(auditLogWriter).writeLog(auditEvent);
        }

        @Test
        @DisplayName("Logs event with complete information")
        void logs_eventWithCompleteInfo() {
            // Given
            auditEvent = AuditEvent.builder()
                    .source(this)
                    .user(testUser)
                    .action("Complete Action")
                    .actionStatus("Success")
                    .message("Detailed message about the action")
                    .ipAddress("10.0.0.1")
                    .userAgent("Chrome/96.0")
                    .sessionId("sess-12345")
                    .extraData("key1=value1,key2=value2")
                    .build();

            // When
            auditEventListener.onApplicationEvent(auditEvent);

            // Then
            verify(auditLogWriter).writeLog(auditEvent);
        }
    }
}