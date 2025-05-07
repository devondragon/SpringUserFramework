package com.digitalsanctuary.spring.user.audit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The FileAuditLogFlushScheduler class is a Spring Boot component that flushes the audit log buffer to the file. This class is used to ensure that
 * the audit log buffer is flushed periodically to balance performance with data integrity.
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
