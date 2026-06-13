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
     * The flush rate, in milliseconds, at which the audit log buffer is flushed to the log file when
     * {@link #flushOnWrite} is {@code false}. May be set to any positive integer. The library default
     * (from {@code dsspringuserconfig.properties}) is {@code 30000} (30 seconds). Smaller values reduce
     * the durability window (the amount of buffered audit data that can be lost on a hard crash) at a
     * small performance cost.
     */
    private int flushRate;

    /**
     * Maximum number of audit events to return from a single query.
     * This prevents unbounded memory usage when querying large audit logs.
     * Set to 0 or negative to disable the limit (not recommended for production).
     * Default is 10000.
     */
    private int maxQueryResults = 10000;

    /**
     * Maximum size of the active audit log file, in megabytes, before it is rotated.
     * When the active log file exceeds this size, it is rotated: the current file is renamed to
     * {@code <name>.1} (shifting any existing {@code <name>.1} to {@code <name>.2}, and so on, up to
     * {@link #maxFiles}) and a fresh active file is opened. Set to {@code 0} or a negative value to
     * disable rotation (logs grow unbounded). Default is {@code 10} (MB).
     */
    private int maxFileSizeMb = 10;

    /**
     * Maximum number of rotated audit log files to keep (e.g. {@code user-audit.log.1} ..
     * {@code user-audit.log.5}). When rotation produces more than this many archived files, the oldest
     * is deleted. Must be at least {@code 1} for rotation to retain any history. Default is {@code 5}.
     */
    private int maxFiles = 5;

}
