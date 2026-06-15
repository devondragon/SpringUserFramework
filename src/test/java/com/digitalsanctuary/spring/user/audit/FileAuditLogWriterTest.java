package com.digitalsanctuary.spring.user.audit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.test.builders.UserTestDataBuilder;

@ExtendWith(MockitoExtension.class)
@DisplayName("FileAuditLogWriter Tests")
class FileAuditLogWriterTest {

    @Mock
    private AuditConfig auditConfig;

    @InjectMocks
    private FileAuditLogWriter fileAuditLogWriter;

    @TempDir
    Path tempDir;

    private User testUser;
    private AuditEvent auditEvent;
    private String logFilePath;

    @BeforeEach
    void setUp() throws IOException {
        logFilePath = tempDir.resolve("audit.log").toString();
        lenient().when(auditConfig.getLogFilePath()).thenReturn(logFilePath);
        lenient().when(auditConfig.isLogEvents()).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        fileAuditLogWriter.cleanup();
    }

    @Nested
    @DisplayName("Null Safety Tests")
    class NullSafetyTests {

        @Test
        @DisplayName("writeLog - handles user with null ID without throwing NPE")
        void writeLog_handlesUserWithNullId() throws IOException {
            // Given
            testUser = UserTestDataBuilder.aUser()
                    .withId(null) // User exists but ID is null (before persistence)
                    .withEmail("newuser@example.com")
                    .withFirstName("New")
                    .withLastName("User")
                    .build();

            auditEvent = AuditEvent.builder()
                    .source(this)
                    .user(testUser)
                    .action("Registration")
                    .actionStatus("Success")
                    .message("New user registration")
                    .ipAddress("192.168.1.1")
                    .build();

            when(auditConfig.isFlushOnWrite()).thenReturn(true);  // Ensure flush happens
            fileAuditLogWriter.setup();

            // When & Then - should not throw
            assertDoesNotThrow(() -> fileAuditLogWriter.writeLog(auditEvent));

            // Verify the log was written with email as fallback
            String logContent = Files.readString(Path.of(logFilePath));
            assertTrue(logContent.contains("newuser@example.com"));
        }

        @Test
        @DisplayName("writeLog - handles null user without throwing")
        void writeLog_handlesNullUser() throws IOException {
            // Given
            auditEvent = AuditEvent.builder()
                    .source(this)
                    .user(null) // No user object at all
                    .action("Failed Login")
                    .actionStatus("Failed")
                    .message("Invalid credentials")
                    .ipAddress("192.168.1.100")
                    .build();

            when(auditConfig.isFlushOnWrite()).thenReturn(true);  // Ensure flush happens
            fileAuditLogWriter.setup();

            // When & Then - should not throw
            assertDoesNotThrow(() -> fileAuditLogWriter.writeLog(auditEvent));

            // Verify the log was written with "unknown" as subject
            String logContent = Files.readString(Path.of(logFilePath));
            assertTrue(logContent.contains("unknown"));
        }

        @Test
        @DisplayName("writeLog - handles user with null ID and null email")
        void writeLog_handlesUserWithNullIdAndEmail() throws IOException {
            // Given
            testUser = UserTestDataBuilder.aUser()
                    .withId(null)
                    .withEmail(null) // Both ID and email are null
                    .withFirstName("Anonymous")
                    .withLastName("User")
                    .build();

            auditEvent = AuditEvent.builder()
                    .source(this)
                    .user(testUser)
                    .action("Action")
                    .actionStatus("Success")
                    .build();

            when(auditConfig.isFlushOnWrite()).thenReturn(true);  // Ensure flush happens
            fileAuditLogWriter.setup();

            // When & Then - should not throw
            assertDoesNotThrow(() -> fileAuditLogWriter.writeLog(auditEvent));

            // Verify the log was written with "unknown" as subject
            String logContent = Files.readString(Path.of(logFilePath));
            assertTrue(logContent.contains("unknown"));
        }

        @Test
        @DisplayName("writeLog - handles normal user with ID correctly")
        void writeLog_handlesNormalUserWithId() throws IOException {
            // Given
            testUser = UserTestDataBuilder.aUser()
                    .withId(123L)
                    .withEmail("user@example.com")
                    .withFirstName("Test")
                    .withLastName("User")
                    .build();

            auditEvent = AuditEvent.builder()
                    .source(this)
                    .user(testUser)
                    .action("Login")
                    .actionStatus("Success")
                    .message("User logged in")
                    .build();

            when(auditConfig.isFlushOnWrite()).thenReturn(true);  // Ensure flush happens
            fileAuditLogWriter.setup();

            // When
            fileAuditLogWriter.writeLog(auditEvent);

            // Then - verify the log contains the user ID
            String logContent = Files.readString(Path.of(logFilePath));
            assertTrue(logContent.contains("123"));
            assertTrue(logContent.contains("user@example.com"));
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("writeLog - handles IOException gracefully")
        void writeLog_handlesIOException() throws Exception {
            // Given
            fileAuditLogWriter.setup();

            // Replace the bufferedWriter with a mock that throws IOException
            BufferedWriter mockWriter = mock(BufferedWriter.class);
            doThrow(new IOException("Test IO Exception")).when(mockWriter).write(anyString());

            Field writerField = FileAuditLogWriter.class.getDeclaredField("bufferedWriter");
            writerField.setAccessible(true);
            writerField.set(fileAuditLogWriter, mockWriter);

            auditEvent = AuditEvent.builder()
                    .source(this)
                    .action("Test")
                    .actionStatus("Test")
                    .build();

            // When & Then - should not throw, errors are logged
            assertDoesNotThrow(() -> fileAuditLogWriter.writeLog(auditEvent));
        }

        @Test
        @DisplayName("writeLog - handles when BufferedWriter is not initialized")
        void writeLog_handlesUninitializedWriter() {
            // Given - writer not initialized (setup not called)
            auditEvent = AuditEvent.builder()
                    .source(this)
                    .action("Test")
                    .actionStatus("Test")
                    .build();

            // When & Then - should not throw
            assertDoesNotThrow(() -> fileAuditLogWriter.writeLog(auditEvent));
        }

        @Test
        @DisplayName("writeLog - handles general exceptions without propagating")
        void writeLog_handlesGeneralException() throws Exception {
            // Given
            fileAuditLogWriter.setup();

            // Create an event that could cause unexpected exceptions
            auditEvent = mock(AuditEvent.class);
            when(auditEvent.getUser()).thenThrow(new RuntimeException("Unexpected error"));

            // When & Then - should not throw
            assertDoesNotThrow(() -> fileAuditLogWriter.writeLog(auditEvent));
        }
    }

    @Nested
    @DisplayName("Setup and Configuration Tests")
    class SetupConfigurationTests {

        @Test
        @DisplayName("setup - handles disabled audit logging")
        void setup_handlesDisabledLogging() {
            // Given
            when(auditConfig.isLogEvents()).thenReturn(false);

            // When
            fileAuditLogWriter.setup();

            // Then - file should not be created
            auditEvent = AuditEvent.builder()
                    .source(this)
                    .action("Test")
                    .actionStatus("Test")
                    .build();

            assertDoesNotThrow(() -> fileAuditLogWriter.writeLog(auditEvent));
        }

        @Test
        @DisplayName("setup - handles null configuration")
        void setup_handlesNullConfig() throws Exception {
            // Given
            FileAuditLogWriter writerWithNullConfig = new FileAuditLogWriter(null);

            // When & Then
            assertDoesNotThrow(() -> writerWithNullConfig.setup());
            
            auditEvent = AuditEvent.builder()
                    .source(this)
                    .action("Test")
                    .actionStatus("Test")
                    .build();

            assertDoesNotThrow(() -> writerWithNullConfig.writeLog(auditEvent));
        }

        @Test
        @DisplayName("setup - handles empty log file path")
        void setup_handlesEmptyLogPath() {
            // Given
            when(auditConfig.getLogFilePath()).thenReturn("");

            // When & Then
            assertDoesNotThrow(() -> fileAuditLogWriter.setup());
        }
    }

    @Nested
    @DisplayName("Flush Tests")
    class FlushTests {

        @Test
        @DisplayName("flushWriter - handles flush when writer is initialized")
        void flushWriter_handlesFlush() throws IOException {
            // Given
            fileAuditLogWriter.setup();

            // When & Then
            assertDoesNotThrow(() -> fileAuditLogWriter.flushWriter());
        }

        @Test
        @DisplayName("flushWriter - handles flush when writer is null")
        void flushWriter_handlesNullWriter() {
            // When & Then - should not throw
            assertDoesNotThrow(() -> fileAuditLogWriter.flushWriter());
        }

        @Test
        @DisplayName("writeLog - flushes when configured")
        void writeLog_flushesWhenConfigured() throws IOException {
            // Given
            when(auditConfig.isFlushOnWrite()).thenReturn(true);
            fileAuditLogWriter.setup();

            auditEvent = AuditEvent.builder()
                    .source(this)
                    .action("Test")
                    .actionStatus("Success")
                    .build();

            // When
            fileAuditLogWriter.writeLog(auditEvent);

            // Then - verify flush was called (file should contain the event immediately)
            String logContent = Files.readString(Path.of(logFilePath));
            assertTrue(logContent.contains("Test"));
        }
    }

    @Nested
    @DisplayName("Rotation Tests")
    class RotationTests {

        private AuditEvent event(String action) {
            return AuditEvent.builder()
                    .source(this)
                    .action(action)
                    .actionStatus("Success")
                    .message("rotation test message padding padding padding")
                    .build();
        }

        @Test
        @DisplayName("rotates the active file when the size threshold is exceeded")
        void rotatesActiveFile_whenThresholdExceeded() throws IOException {
            // Given - a tiny effective byte threshold so a few writes trigger rotation
            when(auditConfig.isFlushOnWrite()).thenReturn(true);
            when(auditConfig.getMaxFileSizeMb()).thenReturn(10); // positive => rotation enabled
            when(auditConfig.getMaxFiles()).thenReturn(5);
            fileAuditLogWriter.setup();
            fileAuditLogWriter.setMaxFileSizeBytesForTesting(120L); // exceeded quickly

            Path active = Path.of(logFilePath);
            Path rotated1 = Path.of(logFilePath + ".1");

            // When - write enough to exceed the threshold
            for (int i = 0; i < 10; i++) {
                fileAuditLogWriter.writeLog(event("Action" + i));
            }

            // Then - a rotated file exists and the active file is fresh (header + recent writes only)
            assertTrue(Files.exists(rotated1), "rotated file .1 should exist after rotation");
            assertTrue(Files.exists(active), "active log file should be reopened after rotation");
            String activeContent = Files.readString(active);
            assertTrue(activeContent.contains("Date|Action|Action Status"),
                    "freshly reopened active file should contain the header");
            assertTrue(Files.size(active) < Files.size(rotated1) + 4096,
                    "active file should be smaller than total rotated content");
        }

        @Test
        @DisplayName("respects maxFiles by deleting the oldest rotated file")
        void respectsMaxFiles_deletingOldest() throws IOException {
            // Given - keep only 2 rotated files
            when(auditConfig.isFlushOnWrite()).thenReturn(true);
            when(auditConfig.getMaxFileSizeMb()).thenReturn(10);
            when(auditConfig.getMaxFiles()).thenReturn(2);
            fileAuditLogWriter.setup();
            fileAuditLogWriter.setMaxFileSizeBytesForTesting(80L);

            Path rotated1 = Path.of(logFilePath + ".1");
            Path rotated2 = Path.of(logFilePath + ".2");
            Path rotated3 = Path.of(logFilePath + ".3");

            // When - force several rotations
            for (int i = 0; i < 40; i++) {
                fileAuditLogWriter.writeLog(event("Action" + i));
            }

            // Then - at most maxFiles rotated files are kept; .3 must never exist
            assertTrue(Files.exists(rotated1), ".1 should exist");
            assertTrue(Files.exists(rotated2), ".2 should exist");
            assertTrue(!Files.exists(rotated3), ".3 should have been deleted (exceeds maxFiles)");
        }

        @Test
        @DisplayName("does not rotate when rotation is disabled (maxFileSizeMb <= 0)")
        void doesNotRotate_whenDisabled() throws IOException {
            // Given - rotation disabled
            when(auditConfig.isFlushOnWrite()).thenReturn(true);
            when(auditConfig.getMaxFileSizeMb()).thenReturn(0);
            fileAuditLogWriter.setup();

            Path rotated1 = Path.of(logFilePath + ".1");

            // When - write a lot
            for (int i = 0; i < 50; i++) {
                fileAuditLogWriter.writeLog(event("Action" + i));
            }

            // Then - no rotation occurred
            assertTrue(!Files.exists(rotated1), "no rotated file should exist when rotation is disabled");
        }
    }

    @Nested
    @DisplayName("Complete Event Data Tests")
    class CompleteEventDataTests {

        @Test
        @DisplayName("writeLog - handles event with all fields populated")
        void writeLog_handlesCompleteEvent() throws IOException {
            // Given
            testUser = UserTestDataBuilder.aUser()
                    .withId(456L)
                    .withEmail("complete@example.com")
                    .build();

            auditEvent = AuditEvent.builder()
                    .source(this)
                    .user(testUser)
                    .action("Complete Action")
                    .actionStatus("Success")
                    .message("Detailed message")
                    .ipAddress("10.0.0.1")
                    .userAgent("Mozilla/5.0")
                    .sessionId("session-123")
                    .extraData("key=value")
                    .build();

            when(auditConfig.isFlushOnWrite()).thenReturn(true);  // Ensure flush happens
            fileAuditLogWriter.setup();

            // When
            fileAuditLogWriter.writeLog(auditEvent);

            // Then
            String logContent = Files.readString(Path.of(logFilePath));
            assertTrue(logContent.contains("456"));
            assertTrue(logContent.contains("complete@example.com"));
            assertTrue(logContent.contains("Complete Action"));
            assertTrue(logContent.contains("Success"));
            assertTrue(logContent.contains("Detailed message"));
            assertTrue(logContent.contains("10.0.0.1"));
            assertTrue(logContent.contains("Mozilla/5.0"));
            assertTrue(logContent.contains("session-123"));
            assertTrue(logContent.contains("key=value"));
        }

        @Test
        @DisplayName("writeLog - handles event with minimal fields")
        void writeLog_handlesMinimalEvent() throws IOException {
            // Given
            auditEvent = AuditEvent.builder()
                    .source(this)
                    .action("Minimal")
                    .actionStatus("Unknown")
                    .build();

            when(auditConfig.isFlushOnWrite()).thenReturn(true);  // Ensure flush happens
            fileAuditLogWriter.setup();

            // When & Then
            assertDoesNotThrow(() -> fileAuditLogWriter.writeLog(auditEvent));

            String logContent = Files.readString(Path.of(logFilePath));
            assertTrue(logContent.contains("Minimal"));
            assertTrue(logContent.contains("Unknown"));
            assertTrue(logContent.contains("unknown")); // Should have "unknown" as subject
        }
    }
}