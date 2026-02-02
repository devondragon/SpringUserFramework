package com.digitalsanctuary.spring.user.audit;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import com.digitalsanctuary.spring.user.persistence.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * File-based implementation of {@link AuditLogQueryService} that parses the
 * pipe-delimited audit log file created by {@link FileAuditLogWriter}.
 *
 * <p>This implementation reads and parses the entire log file for each query,
 * filtering results by user email or ID. While suitable for small to medium
 * audit volumes, applications with high audit volumes should consider implementing
 * a database-backed query service.
 *
 * <p>The log file format is:
 * {@code Date|Action|ActionStatus|UserId|Email|IPAddress|SessionId|Message|UserAgent|ExtraData}
 *
 * @see AuditLogQueryService
 * @see FileAuditLogWriter
 * @see AuditConfig
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileAuditLogQueryService implements AuditLogQueryService {

    private final AuditConfig auditConfig;

    /**
     * DateTimeFormatter patterns to try when parsing dates from the log file.
     * MessageFormat produces dates like "Jan 15, 2025, 3:45:30 PM" or similar.
     */
    private static final DateTimeFormatter[] DATE_FORMATTERS = {
            DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss zzz yyyy", Locale.US),
            DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm:ss a", Locale.US),
            DateTimeFormatter.ofPattern("MMM dd, yyyy, h:mm:ss a", Locale.US),
            DateTimeFormatter.ISO_INSTANT,
            DateTimeFormatter.ISO_DATE_TIME
    };

    @Override
    public List<AuditEventDTO> findByUser(User user) {
        return findByUser(user, null, null);
    }

    @Override
    public List<AuditEventDTO> findByUserSince(User user, Instant since) {
        return findByUser(user, since, null);
    }

    @Override
    public List<AuditEventDTO> findByUserAndAction(User user, String action) {
        return findByUser(user, null, action);
    }

    /**
     * Internal method to find audit events with optional filtering.
     *
     * @param user the user to filter by
     * @param since optional timestamp filter
     * @param action optional action filter
     * @return filtered list of audit events
     */
    private List<AuditEventDTO> findByUser(User user, Instant since, String action) {
        if (user == null) {
            return Collections.emptyList();
        }

        Path logPath = getLogFilePath();
        if (logPath == null || !Files.exists(logPath)) {
            log.debug("FileAuditLogQueryService.findByUser: Audit log file not found");
            return Collections.emptyList();
        }

        List<AuditEventDTO> results = new ArrayList<>();
        String userEmail = user.getEmail();
        String userId = user.getId() != null ? user.getId().toString() : null;

        try (BufferedReader reader = Files.newBufferedReader(logPath)) {
            String line;
            boolean isFirstLine = true;

            while ((line = reader.readLine()) != null) {
                // Skip header line
                if (isFirstLine) {
                    isFirstLine = false;
                    if (line.startsWith("Date|Action")) {
                        continue;
                    }
                }

                AuditEventDTO event = parseLine(line);
                if (event == null) {
                    continue;
                }

                // Filter by user
                if (!matchesUser(event, userEmail, userId)) {
                    continue;
                }

                // Filter by timestamp if specified
                if (since != null && event.getTimestamp() != null && event.getTimestamp().isBefore(since)) {
                    continue;
                }

                // Filter by action if specified
                if (action != null && !action.equals(event.getAction())) {
                    continue;
                }

                results.add(event);
            }
        } catch (IOException e) {
            log.error("FileAuditLogQueryService.findByUser: Error reading audit log file", e);
            return Collections.emptyList();
        }

        // Sort by timestamp descending (most recent first)
        results.sort(Comparator.comparing(AuditEventDTO::getTimestamp,
                Comparator.nullsLast(Comparator.reverseOrder())));

        return results;
    }

    /**
     * Gets the path to the audit log file, checking both configured path and fallback.
     *
     * @return the path to the log file, or null if not found
     */
    private Path getLogFilePath() {
        if (auditConfig == null || auditConfig.getLogFilePath() == null) {
            return null;
        }

        Path configuredPath = Path.of(auditConfig.getLogFilePath());
        if (Files.exists(configuredPath)) {
            return configuredPath;
        }

        // Check fallback temp directory location
        Path tempPath = Path.of(System.getProperty("java.io.tmpdir") + File.separator + "user-audit.log");
        if (Files.exists(tempPath)) {
            return tempPath;
        }

        return null;
    }

    /**
     * Parses a single line from the audit log file.
     *
     * @param line the line to parse
     * @return the parsed AuditEventDTO, or null if parsing fails
     */
    private AuditEventDTO parseLine(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }

        String[] parts = line.split("\\|", -1); // -1 to keep trailing empty strings
        if (parts.length < 10) {
            log.debug("FileAuditLogQueryService.parseLine: Invalid line format, expected 10 fields: {}", line);
            return null;
        }

        try {
            return AuditEventDTO.builder()
                    .timestamp(parseTimestamp(parts[0]))
                    .action(nullIfEmpty(parts[1]))
                    .actionStatus(nullIfEmpty(parts[2]))
                    .userId(nullIfEmpty(parts[3]))
                    .userEmail(nullIfEmpty(parts[4]))
                    .ipAddress(nullIfEmpty(parts[5]))
                    .sessionId(nullIfEmpty(parts[6]))
                    .message(nullIfEmpty(parts[7]))
                    .userAgent(nullIfEmpty(parts[8]))
                    .extraData(nullIfEmpty(parts[9]))
                    .build();
        } catch (Exception e) {
            log.debug("FileAuditLogQueryService.parseLine: Error parsing line: {}", line, e);
            return null;
        }
    }

    /**
     * Parses a timestamp string from the log file.
     *
     * @param dateStr the date string to parse
     * @return the parsed Instant, or null if parsing fails
     */
    private Instant parseTimestamp(String dateStr) {
        if (dateStr == null || dateStr.isBlank() || "null".equals(dateStr)) {
            return null;
        }

        // Try each formatter
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                ZonedDateTime zdt = ZonedDateTime.parse(dateStr.trim(), formatter);
                return zdt.toInstant();
            } catch (DateTimeParseException e) {
                // Try next formatter
            }
        }

        // Try parsing as epoch millis
        try {
            long epochMillis = Long.parseLong(dateStr.trim());
            return Instant.ofEpochMilli(epochMillis);
        } catch (NumberFormatException e) {
            // Not a number
        }

        log.debug("FileAuditLogQueryService.parseTimestamp: Could not parse date: {}", dateStr);
        return null;
    }

    /**
     * Checks if an audit event matches the given user.
     *
     * @param event the event to check
     * @param userEmail the user's email
     * @param userId the user's ID
     * @return true if the event matches the user
     */
    private boolean matchesUser(AuditEventDTO event, String userEmail, String userId) {
        // Match by email
        if (userEmail != null && userEmail.equalsIgnoreCase(event.getUserEmail())) {
            return true;
        }

        // Match by user ID
        if (userId != null && userId.equals(event.getUserId())) {
            return true;
        }

        return false;
    }

    /**
     * Returns null if the string is empty or "null".
     */
    private String nullIfEmpty(String value) {
        if (value == null || value.isBlank() || "null".equals(value)) {
            return null;
        }
        return value.trim();
    }

}
