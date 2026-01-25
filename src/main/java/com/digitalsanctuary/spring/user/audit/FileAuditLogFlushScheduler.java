package com.digitalsanctuary.spring.user.audit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

/**
 * Scheduled task that periodically flushes the audit log buffer to disk.
 *
 * <p>This component ensures buffered audit data is written to the file at regular intervals,
 * balancing write performance with data integrity.
 *
 * <p><strong>Conditional Activation:</strong> This scheduler is only active when both conditions are met:
 * <ul>
 *   <li>{@code user.audit.logEvents=true} - audit logging is enabled</li>
 *   <li>{@code user.audit.flushOnWrite=false} - immediate flush is disabled</li>
 * </ul>
 *
 * <p>When flush-on-write is enabled, logs are flushed immediately after each write,
 * making this scheduler unnecessary. The flush frequency is controlled by
 * {@code user.audit.flushRate} (in milliseconds).
 *
 * @see FileAuditLogWriter
 * @see AuditConfig
 */
@Slf4j
@Component
@ConditionalOnExpression("${user.audit.logEvents:true} && !${user.audit.flushOnWrite:true}")
public class FileAuditLogFlushScheduler {

    private final FileAuditLogWriter fileAuditLogWriter;

    /**
     * Creates a new FileAuditLogFlushScheduler with the required dependencies.
     *
     * @param fileAuditLogWriter the file audit log writer to flush
     */
    public FileAuditLogFlushScheduler(FileAuditLogWriter fileAuditLogWriter) {
        this.fileAuditLogWriter = fileAuditLogWriter;
    }

    /**
     * Flushes the audit log buffer to the file. This method is called on a schedule to ensure that the buffer is flushed periodically to balance
     * performance with data integrity.
     */
    @Scheduled(fixedRateString = "#{@auditConfig.flushRate}")
    public void flushAuditLog() {
        log.info("FileAuditLogFlushScheduler.flushAuditLog: Flushing audit log buffer to file.");
        fileAuditLogWriter.flushWriter();
    }
}
