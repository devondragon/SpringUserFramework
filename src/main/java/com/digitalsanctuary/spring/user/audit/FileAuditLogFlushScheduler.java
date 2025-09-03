package com.digitalsanctuary.spring.user.audit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The FileAuditLogFlushScheduler class is a Spring Boot component that flushes the audit log buffer to the file. This class is used to ensure that
 * the audit log buffer is flushed periodically to balance performance with data integrity.
 * 
 * <p><strong>Conditional Activation Logic:</strong></p>
 * <p>This scheduler is only active when BOTH conditions are met:</p>
 * <ul>
 *   <li><code>user.audit.logEvents=true</code> (audit logging is enabled) AND</li>
 *   <li><code>user.audit.flushOnWrite=false</code> (immediate flush is disabled)</li>
 * </ul>
 * 
 * <p><strong>Why this conditional logic?</strong></p>
 * <ul>
 *   <li>If audit logging is disabled (<code>logEvents=false</code>), no scheduler is needed</li>
 *   <li>If flush-on-write is enabled (<code>flushOnWrite=true</code>), logs are flushed immediately after each write, 
 *       so periodic flushing is unnecessary and would be redundant</li>
 *   <li>Only when audit logging is enabled AND flush-on-write is disabled do we need this periodic scheduler 
 *       to ensure buffered data gets written to disk at regular intervals</li>
 * </ul>
 * 
 * <p>The flush frequency is controlled by <code>user.audit.flushRate</code> (in milliseconds).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnExpression("${user.audit.logEvents:true} && !${user.audit.flushOnWrite:true}")
public class FileAuditLogFlushScheduler {

    /**
     * The file audit log writer. This is the writer that is used to write audit log events to the file.
     */
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
