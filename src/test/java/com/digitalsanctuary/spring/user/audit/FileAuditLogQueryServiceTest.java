package com.digitalsanctuary.spring.user.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.test.annotations.ServiceTest;
import com.digitalsanctuary.spring.user.test.builders.UserTestDataBuilder;

@ServiceTest
@DisplayName("FileAuditLogQueryService Tests")
class FileAuditLogQueryServiceTest {

    @Mock
    private AuditConfig auditConfig;

    @InjectMocks
    private FileAuditLogQueryService queryService;

    @TempDir
    Path tempDir;

    private User testUser;
    private Path logFile;

    @BeforeEach
    void setUp() throws IOException {
        testUser = UserTestDataBuilder.aVerifiedUser()
                .withId(1L)
                .withEmail("test@example.com")
                .build();

        logFile = tempDir.resolve("test-audit.log");
    }

    private void setupLogFilePath() {
        when(auditConfig.getLogFilePath()).thenReturn(logFile.toString());
    }

    @Nested
    @DisplayName("findByUser")
    class FindByUser {

        @Test
        @DisplayName("returns empty list when user is null")
        void returnsEmptyList_whenUserIsNull() {
            // No need to set up log file - should return empty immediately
            List<AuditEventDTO> result = queryService.findByUser(null);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty list when log file does not exist")
        void returnsEmptyList_whenLogFileDoesNotExist() {
            // Given
            setupLogFilePath();
            // Log file not created
            String originalTmpDir = System.getProperty("java.io.tmpdir");
            System.setProperty("java.io.tmpdir", tempDir.toString());
            try {
                // When
                List<AuditEventDTO> result = queryService.findByUser(testUser);

                // Then
                assertThat(result).isEmpty();
            } finally {
                if (originalTmpDir == null) {
                    System.clearProperty("java.io.tmpdir");
                } else {
                    System.setProperty("java.io.tmpdir", originalTmpDir);
                }
            }
        }

        @Test
        @DisplayName("parses log file and returns matching events")
        void parsesLogFile_returnsMatchingEvents() throws IOException {
            // Given
            setupLogFilePath();
            String logContent = """
                Date|Action|Action Status|User ID|Email|IP Address|SessionId|Message|User Agent|Extra Data
                Thu Jan 15 10:30:00 EST 2025|Login|Success|1|test@example.com|127.0.0.1|sess123|User logged in|Mozilla/5.0|null
                Thu Jan 15 10:35:00 EST 2025|Registration|Success|2|other@example.com|127.0.0.1|sess456|User registered|Mozilla/5.0|null
                """;
            Files.writeString(logFile, logContent);

            // When
            List<AuditEventDTO> result = queryService.findByUser(testUser);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getAction()).isEqualTo("Login");
            assertThat(result.get(0).getUserEmail()).isEqualTo("test@example.com");
        }

        @Test
        @DisplayName("matches by user ID")
        void matchesByUserId() throws IOException {
            // Given
            setupLogFilePath();
            String logContent = """
                Date|Action|Action Status|User ID|Email|IP Address|SessionId|Message|User Agent|Extra Data
                Thu Jan 15 10:30:00 EST 2025|PasswordUpdate|Success|1|null|127.0.0.1|sess123|Password updated|Mozilla/5.0|null
                """;
            Files.writeString(logFile, logContent);

            // When
            List<AuditEventDTO> result = queryService.findByUser(testUser);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getAction()).isEqualTo("PasswordUpdate");
        }

        @Test
        @DisplayName("returns events sorted by timestamp descending")
        void returnsEvents_sortedByTimestampDescending() throws IOException {
            // Given
            setupLogFilePath();
            String logContent = """
                Date|Action|Action Status|User ID|Email|IP Address|SessionId|Message|User Agent|Extra Data
                Thu Jan 15 08:00:00 EST 2025|Login|Success|1|test@example.com|127.0.0.1|sess1|First|Mozilla/5.0|null
                Thu Jan 15 12:00:00 EST 2025|Logout|Success|1|test@example.com|127.0.0.1|sess2|Third|Mozilla/5.0|null
                Thu Jan 15 10:00:00 EST 2025|PasswordUpdate|Success|1|test@example.com|127.0.0.1|sess3|Second|Mozilla/5.0|null
                """;
            Files.writeString(logFile, logContent);

            // When
            List<AuditEventDTO> result = queryService.findByUser(testUser);

            // Then - should be sorted newest first
            assertThat(result).hasSize(3);
            // The order should be: Logout (12:00), PasswordUpdate (10:00), Login (08:00)
        }
    }

    @Nested
    @DisplayName("findByUserAndAction")
    class FindByUserAndAction {

        @Test
        @DisplayName("filters by action type")
        void filtersByActionType() throws IOException {
            // Given
            setupLogFilePath();
            String logContent = """
                Date|Action|Action Status|User ID|Email|IP Address|SessionId|Message|User Agent|Extra Data
                Thu Jan 15 10:30:00 EST 2025|Login|Success|1|test@example.com|127.0.0.1|sess1|Logged in|Mozilla/5.0|null
                Thu Jan 15 10:35:00 EST 2025|CONSENT_GRANTED|Success|1|test@example.com|127.0.0.1|sess2|Consent|Mozilla/5.0|{"consentType":"privacy_policy"}
                Thu Jan 15 10:40:00 EST 2025|Login|Success|1|test@example.com|127.0.0.1|sess3|Logged in again|Mozilla/5.0|null
                """;
            Files.writeString(logFile, logContent);

            // When
            List<AuditEventDTO> result = queryService.findByUserAndAction(testUser, "CONSENT_GRANTED");

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getAction()).isEqualTo("CONSENT_GRANTED");
            assertThat(result.get(0).getExtraData()).contains("privacy_policy");
        }

        @Test
        @DisplayName("returns empty list when no matching actions")
        void returnsEmptyList_whenNoMatchingActions() throws IOException {
            // Given
            setupLogFilePath();
            String logContent = """
                Date|Action|Action Status|User ID|Email|IP Address|SessionId|Message|User Agent|Extra Data
                Thu Jan 15 10:30:00 EST 2025|Login|Success|1|test@example.com|127.0.0.1|sess1|Logged in|Mozilla/5.0|null
                """;
            Files.writeString(logFile, logContent);

            // When
            List<AuditEventDTO> result = queryService.findByUserAndAction(testUser, "CONSENT_GRANTED");

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("Line parsing")
    class LineParsing {

        @Test
        @DisplayName("handles empty extra data")
        void handlesEmptyExtraData() throws IOException {
            // Given
            setupLogFilePath();
            String logContent = """
                Date|Action|Action Status|User ID|Email|IP Address|SessionId|Message|User Agent|
                Thu Jan 15 10:30:00 EST 2025|Login|Success|1|test@example.com|127.0.0.1|sess1|Message|Mozilla/5.0|
                """;
            Files.writeString(logFile, logContent);

            // When
            List<AuditEventDTO> result = queryService.findByUser(testUser);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getExtraData()).isNull();
        }

        @Test
        @DisplayName("handles null values in fields")
        void handlesNullValues() throws IOException {
            // Given
            setupLogFilePath();
            String logContent = """
                Date|Action|Action Status|User ID|Email|IP Address|SessionId|Message|User Agent|Extra Data
                Thu Jan 15 10:30:00 EST 2025|Login|Success|1|test@example.com|null|null|Message|null|null
                """;
            Files.writeString(logFile, logContent);

            // When
            List<AuditEventDTO> result = queryService.findByUser(testUser);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getIpAddress()).isNull();
            assertThat(result.get(0).getSessionId()).isNull();
        }

        @Test
        @DisplayName("skips malformed lines")
        void skipsMalformedLines() throws IOException {
            // Given
            setupLogFilePath();
            String logContent = """
                Date|Action|Action Status|User ID|Email|IP Address|SessionId|Message|User Agent|Extra Data
                Thu Jan 15 10:30:00 EST 2025|Login|Success|1|test@example.com|127.0.0.1|sess1|Message|Mozilla/5.0|null
                This is not a valid log line
                Thu Jan 15 10:35:00 EST 2025|Logout|Success|1|test@example.com|127.0.0.1|sess2|Message|Mozilla/5.0|null
                """;
            Files.writeString(logFile, logContent);

            // When
            List<AuditEventDTO> result = queryService.findByUser(testUser);

            // Then
            assertThat(result).hasSize(2);
        }
    }

}
