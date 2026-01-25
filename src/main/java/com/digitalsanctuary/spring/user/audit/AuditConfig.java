package com.digitalsanctuary.spring.user.audit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;
import lombok.Data;

/**
 * Configuration properties for the user audit logging system.
 *
 * <p>This class defines properties that control the behavior of audit logging,
 * including the log file path, flush behavior, and event logging toggle.
 * Properties are bound from the {@code user.audit.*} prefix in application configuration.
 *
 * <p>The class uses Lombok's {@code @Data} annotation which generates getters, setters,
 * and a default no-argument constructor.
 *
 * @see AuditLogWriter
 * @see AuditEventListener
 */
@Data
@Component
@PropertySource("classpath:config/dsspringuserconfig.properties")
@ConfigurationProperties(prefix = "user.audit")
public class AuditConfig {

    /**
     * The enabled flag. If set to false, audit logging will be disabled.
     */
    private boolean logEvents;

    /**
     * The log file path. This is the path to the log file where audit events will be written. The path can be absolute or relative to the application
     */
    private String logFilePath;

    /**
     * The flush on write flag, if enabled, causes the BufferedWriter to be flushed on every log entry. This has a performance impact under heavy
     * loads, but ensures events are written to the log file without delay. This is beneficial in development environments, or environments where the
     * performance penalty is less important that ensuring events are not lost in case of JVM or server crash.
     */
    private boolean flushOnWrite;

    /**
     * The flush rate. This is the rate at which the audit log buffer is flushed to the log file. The value is in milliseconds and can be set to any
     * positive integer. The default value is 1000 (1 second).
     */
    private int flushRate;

}
