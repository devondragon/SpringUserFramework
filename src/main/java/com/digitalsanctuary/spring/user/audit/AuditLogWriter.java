package com.digitalsanctuary.spring.user.audit;

/**
 * Interface for writing audit log
 *
 *
 *
 * <p>
 * Implementations of this interface are responsible for writing audit log messages to a log file or other destination.
 * </p>
 * <p>
 * This can include writing to a file, writing to a database, sending messages to a REST API, SIEM or any other method of storing or transmitting
 * audit log messages.
 * </p>
 */
public interface AuditLogWriter {

    /**
     * Write an audit log message
     *
     * @param event the audit event to log
     */
    void writeLog(AuditEvent event);

    /**
     * Setup the audit log writer
     */
    void setup();

    /**
     * Cleanup the audit log writer
     */
    void cleanup();
}
