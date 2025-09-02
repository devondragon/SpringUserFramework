package com.digitalsanctuary.spring.user.audit;

import java.io.BufferedWriter;
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
            String userId = event.getUser() != null ? event.getUser().getId().toString() : null;
            String userEmail = event.getUser() != null ? event.getUser().getEmail() : null;
            String output = MessageFormat.format("{0}|{1}|{2}|{3}|{4}|{5}|{6}|{7}|{8}|{9}", event.getDate(), event.getAction(),
                    event.getActionStatus(), userId, userEmail, event.getIpAddress(), event.getSessionId(), event.getMessage(), event.getUserAgent(),
                    event.getExtraData());
            bufferedWriter.write(output);
            bufferedWriter.newLine();
            if (auditConfig.isFlushOnWrite()) {
                bufferedWriter.flush();
            }
        } catch (IOException e) {
            log.error("FileAuditLogWriter.writeLog: IOException writing to log file: {}", auditConfig.getLogFilePath(), e);
        }
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
     */
    private void openLogFile() {
        String logFilePath = auditConfig.getLogFilePath();
        log.debug("FileAuditLogWriter.setup: Opening log file: {}", logFilePath);
        try {
            OpenOption[] fileOptions = {StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE};
            boolean newFile = Files.notExists(Path.of(logFilePath));
            bufferedWriter = Files.newBufferedWriter(Path.of(logFilePath), fileOptions);
            if (newFile) {
                writeHeader();
            }
            log.info("FileAuditLogWriter.setup: Log file opened.");
        } catch (IOException e) {
            log.error("FileAuditLogWriter.setup: IOException trying to open log file: {}", logFilePath, e);
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
