package com.digitalsanctuary.spring.user.audit;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.MessageFormat;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link AuditLogWriter} that writes audit logs to a file. This class handles the lifecycle of the log file, including opening,
 * writing, and closing the file. It also supports scheduled flushing of the buffer to balance performance with data integrity.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileAuditLogWriter implements AuditLogWriter {

    private final AuditConfig auditConfig;
    private BufferedWriter bufferedWriter;

    /**
     * Initializes the log file writer. This method is called after the bean is constructed. It validates the configuration and opens the log file for
     * writing.
     */
    @PostConstruct
    @Override
    public synchronized void setup() {
        log.info("FileAuditLogWriter.setup: Entering...");
        if (!validateConfig()) {
            return;
        }
        openLogFile();
    }

    /**
     * Cleans up the log file writer. This method is called before the bean is destroyed. It closes the log file to ensure all data is flushed and
     * resources are released.
     */
    @PreDestroy
    @Override
    public synchronized void cleanup() {
        log.info("FileAuditLogWriter.cleanup: Closing log file.");
        closeLogFile();
    }

    /**
     * Writes an audit event to the log file. The event data is formatted and written as a single line. If the buffered writer is not initialized, an
     * error is logged.
     *
     * @param event the audit event to write
     */
    @Override
    public synchronized void writeLog(AuditEvent event) {
        if (bufferedWriter == null) {
            log.error("FileAuditLogWriter.writeLog: BufferedWriter is not initialized.");
            return;
        }
        try {
            // Null-safe extraction of user ID and email
            String userId = null;
            String userEmail = null;
            
            if (event.getUser() != null) {
                Long id = event.getUser().getId();
                userId = (id != null) ? id.toString() : null;
                userEmail = event.getUser().getEmail();
            }
            
            // If no user ID available, use email or "unknown" for the userId field
            if (userId == null) {
                if (userEmail != null) {
                    userId = userEmail;  // Use email as identifier when ID is null
                } else {
                    userId = "unknown";  // Fallback when both are null
                }
            }
            
            // Sanitize every text field before writing it into the pipe-delimited, line-oriented record.
            // Fields such as user-agent, message, email and extra data can be attacker-influenced; an embedded
            // newline would forge a fake record and an embedded pipe would shift columns. Stripping CR/LF and the
            // delimiter guarantees each record stays on one line with exactly ten fields. The date is rendered by
            // MessageFormat (no user content) so the query-service timestamp parser remains compatible.
            String output = MessageFormat.format("{0}|{1}|{2}|{3}|{4}|{5}|{6}|{7}|{8}|{9}", event.getDate(),
                    sanitizeField(event.getAction()), sanitizeField(event.getActionStatus()), sanitizeField(userId),
                    sanitizeField(userEmail), sanitizeField(event.getIpAddress()), sanitizeField(event.getSessionId()),
                    sanitizeField(event.getMessage()), sanitizeField(event.getUserAgent()), sanitizeField(event.getExtraData()));
            bufferedWriter.write(output);
            bufferedWriter.newLine();
            if (auditConfig.isFlushOnWrite()) {
                bufferedWriter.flush();
            }
        } catch (IOException e) {
            log.error("FileAuditLogWriter.writeLog: IOException writing to log file: {}", auditConfig.getLogFilePath(), e);
        } catch (Exception e) {
            // Never let audit failures impact app flow
            log.error("FileAuditLogWriter.writeLog: Failed to write audit log (suppressed): {}", e.getMessage(), e);
        }
    }


    /**
     * Sanitizes a single field for the pipe-delimited, line-oriented audit format by removing CR, LF and the
     * {@code |} delimiter (each replaced with a single space). This prevents log forging (an injected newline
     * starting a fake record) and field corruption (an injected delimiter shifting columns) from
     * attacker-influenced values such as the user agent, message, email, or extra data.
     *
     * @param value the raw field value (may be {@code null})
     * @return the value with CR/LF/{@code |} replaced by spaces, or an empty string when {@code value} is null
     */
    private static String sanitizeField(final Object value) {
        if (value == null) {
            return "";
        }
        return value.toString().replaceAll("[\\r\\n|]", " ");
    }


    /**
     * Flushes the buffered writer to ensure all data is written to the log file. This method is called by the {@link FileAuditLogFlushScheduler} to
     * ensure that the buffer is flushed periodically to balance performance with data integrity.
     */
    public synchronized void flushWriter() {
        if (bufferedWriter != null) {
            try {
                bufferedWriter.flush();
            } catch (IOException e) {
                log.error("FileAuditLogWriter.flushWriter: IOException flushing buffer!", e);
            }
        }
    }

    /**
     * Validates the audit configuration to ensure it is properly set up. Logs errors if the configuration is invalid.
     *
     * @return true if the configuration is valid, false otherwise
     */
    private boolean validateConfig() {
        if (auditConfig == null) {
            log.error("FileAuditLogWriter.setup: No AuditConfig has been configured!");
            return false;
        }
        if (!auditConfig.isLogEvents()) {
            log.info("FileAuditLogWriter.setup: Audit logging is disabled.");
            return false;
        }
        if (!StringUtils.hasText(auditConfig.getLogFilePath())) {
            log.error("FileAuditLogWriter.setup: No user.audit.logFilePath has been configured!");
            return false;
        }
        return true;
    }

    /**
     * Opens the log file for writing. If the file does not exist, it is created. If the file is newly created, a header is written to the file.
     * If the configured path is not writable, falls back to a temporary directory.
     */
    private void openLogFile() {
        String logFilePath = auditConfig.getLogFilePath();
        log.debug("FileAuditLogWriter.setup: Opening log file: {}", logFilePath);
        
        // Try the configured path first
        if (tryOpenLogFile(logFilePath)) {
            return;
        }
        
        // Fall back to temp directory with graceful handling
        String tempLogPath = System.getProperty("java.io.tmpdir") + File.separator + "user-audit.log";
        log.warn("FileAuditLogWriter.setup: Configured log path '{}' is not writable. Falling back to temp directory: {}", 
                 logFilePath, tempLogPath);
        
        if (tryOpenLogFile(tempLogPath)) {
            log.info("FileAuditLogWriter.setup: Successfully opened fallback log file in temp directory");
            return;
        }
        
        // If both fail, log the error
        log.error("FileAuditLogWriter.setup: Unable to open audit log file at configured path '{}' or fallback path '{}'. Audit logging will be disabled.", 
                  logFilePath, tempLogPath);
    }
    
    /**
     * Attempts to open a log file at the specified path.
     * 
     * @param filePath the path to the log file
     * @return true if the file was successfully opened, false otherwise
     */
    private boolean tryOpenLogFile(String filePath) {
        try {
            // Ensure parent directory exists
            Path path = Path.of(filePath);
            Path parentDir = path.getParent();
            if (parentDir != null && Files.notExists(parentDir)) {
                Files.createDirectories(parentDir);
            }
            
            OpenOption[] fileOptions = {StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE};
            boolean newFile = Files.notExists(path);
            bufferedWriter = Files.newBufferedWriter(path, fileOptions);
            
            if (newFile) {
                writeHeader();
            }
            
            log.info("FileAuditLogWriter.setup: Log file opened successfully: {}", filePath);
            return true;
            
        } catch (IOException e) {
            log.debug("FileAuditLogWriter.setup: Failed to open log file at '{}': {}", filePath, e.getMessage());
            return false;
        }
    }

    /**
     * Closes the log file to ensure all data is flushed and resources are released.
     */
    private void closeLogFile() {
        try {
            if (bufferedWriter != null) {
                bufferedWriter.close();
            }
        } catch (IOException e) {
            log.error("FileAuditLogWriter.cleanup: IOException closing log file: {}", auditConfig.getLogFilePath(), e);
        }
    }

    /**
     * Writes a header to the log file. This method is called when the log file is newly created.
     */
    private void writeHeader() {
        log.debug("FileAuditLogWriter.writeHeader: writing header.");
        if (bufferedWriter != null) {
            String output = MessageFormat.format("{0}|{1}|{2}|{3}|{4}|{5}|{6}|{7}|{8}|{9}", "Date", "Action", "Action Status", "User ID", "Email",
                    "IP Address", "SessionId", "Message", "User Agent", "Extra Data");
            try {
                bufferedWriter.write(output);
                bufferedWriter.newLine();
                bufferedWriter.flush();
            } catch (IOException e) {
                log.error("FileAuditLogWriter.writeHeader: IOException writing header: {}", output, e);
            }
        }
    }
}
