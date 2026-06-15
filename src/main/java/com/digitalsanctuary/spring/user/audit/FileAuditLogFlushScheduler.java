package com.digitalsanctuary.spring.user.audit;

import org.springframework.scheduling.annotation.Scheduled;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Scheduled task that periodically flushes the audit log buffer to disk.
 *
 * <p>This component ensures buffered audit data is written to the file at regular intervals,
 * balancing write performance with data integrity.
 *
 * <p><strong>Conditional Activation:</strong> This scheduler is contributed as a {@code @Bean} by
 * {@link AuditMailAutoConfiguration} only when all of these hold:
 * <ul>
 *   <li>{@code user.audit.logEvents=true} - audit logging is enabled</li>
 *   <li>{@code user.audit.flushOnWrite=false} - immediate flush is disabled</li>
 *   <li>a {@link FileAuditLogWriter} bean is present (i.e. a consumer has not replaced the writer)</li>
 * </ul>
 *
 * <p>When flush-on-write is enabled, logs are flushed immediately after each write,
 * making this scheduler unnecessary. The flush frequency is controlled by
 * {@code user.audit.flushRate} (in milliseconds). It is not component-scanned because it depends on the
 * auto-configured {@link FileAuditLogWriter} and must back off when that writer is absent.
 *
 * @see FileAuditLogWriter
 * @see AuditConfig
 * @see AuditMailAutoConfiguration
 */
@Slf4j
@RequiredArgsConstructor
public class FileAuditLogFlushScheduler {

    private final FileAuditLogWriter fileAuditLogWriter;

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
