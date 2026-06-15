package com.digitalsanctuary.spring.user.audit;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import com.digitalsanctuary.spring.user.persistence.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * File-based implementation of {@link AuditLogQueryService} that parses the
 * pipe-delimited audit log file created by {@link FileAuditLogWriter}.
 *
 * <p>This implementation streams the active log file once per query, filtering
 * results by user email or ID. To bound memory and CPU on large files, it retains
 * only the most recent {@code user.audit.maxQueryResults} matching events in a
 * bounded ring buffer rather than loading and sorting the whole file. While
 * suitable for small to medium audit volumes (&lt;50MB, &lt;100K events),
 * applications with high audit volumes or frequent export requests should consider
 * implementing a database-backed query service for better performance.
 *
 * <p><strong>Performance Note:</strong> GDPR export operations call this service
 * multiple times (findByUser, findByUserAndAction); each call streams the active
 * log file once. Memory per call is bounded to {@code maxQueryResults} events. For
 * production deployments with large audit logs, consider:
 * <ul>
 *   <li>Implementing a database-backed {@link AuditLogQueryService}</li>
 *   <li>Adding log rotation to keep file sizes manageable</li>
 *   <li>Using indexed storage for audit events</li>
 * </ul>
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
     * <p><strong>Bounded memory/CPU:</strong> The log file is written in append order (oldest first,
     * newest last). Rather than parsing and sorting the entire file in memory, this method streams the
     * file once and retains only the last {@code maxQueryResults} <em>matching</em> raw lines in a bounded
     * {@link ArrayDeque} ring buffer. Only that bounded window is then parsed and sorted by timestamp
     * descending. Memory is therefore {@code O(maxQueryResults)} regardless of file size, and the sort
     * cost is bounded to the result window rather than the whole file.
     *
     * <p>For result sets {@code <= maxQueryResults} the observable output (filters + newest-first
     * ordering) is identical to the previous full-file implementation. For larger sets, the most-recent
     * {@code maxQueryResults} matches (by file/append order) are returned, then ordered newest-first.
     *
     * <p><strong>Scope:</strong> Only the active log file is queried; rotated archive files
     * ({@code <name>.1}, {@code <name>.2}, ...) are not included. This preserves the prior behavior; query
     * results reflect only the currently-active audit log.
     *
     * @param user the user to filter by
     * @param since optional timestamp filter
     * @param action optional action filter
     * @return filtered list of audit events, newest first, capped at {@code maxQueryResults}
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

        String userEmail = user.getEmail();
        String userId = user.getId() != null ? user.getId().toString() : null;

        int maxResults = auditConfig.getMaxQueryResults();

        // Bounded ring buffer of the most recent matching parsed events in file (append) order.
        // When maxResults <= 0 the limit is disabled and all matching events are retained.
        Deque<AuditEventDTO> window = new ArrayDeque<>();

        try (Stream<String> lines = Files.lines(logPath)) {
            lines.skip(1) // Skip header line
                    .map(this::parseLine)
                    .filter(Objects::nonNull)
                    .filter(event -> matchesUser(event, userEmail, userId))
                    .filter(event -> since == null || event.getTimestamp() == null ||
                            !event.getTimestamp().isBefore(since))
                    .filter(event -> action == null || action.equals(event.getAction()))
                    .forEach(event -> {
                        window.addLast(event);
                        if (maxResults > 0 && window.size() > maxResults) {
                            window.removeFirst(); // evict oldest to keep only the most recent N
                        }
                    });
        } catch (IOException e) {
            log.error("FileAuditLogQueryService.findByUser: Error reading audit log file", e);
            return Collections.emptyList();
        }

        // Sort only the bounded window by timestamp descending (newest first).
        return window.stream()
                .sorted(Comparator.comparing(AuditEventDTO::getTimestamp,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
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
     * <p><b>Note:</b> {@code FileAuditLogWriter} sanitizes each field (stripping CR/LF and the {@code |}
     * delimiter) before writing, so records produced by this library always have exactly ten fields on a
     * single line. The defensive rejoin below remains only to tolerate pre-existing log files written before
     * that sanitization, or files produced by other tooling. A structured format (JSON lines) is still the
     * better long-term choice for deployments that ingest fully untrusted audit input.
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
            log.debug("FileAuditLogQueryService.parseLine: Invalid line format, expected 10 fields but got {}: {}",
                    parts.length, line);
            return null;
        }

        // Defensive: If more than 10 fields exist due to unescaped pipes in message,
        // join the extra parts back into the message field
        if (parts.length > 10) {
            log.debug("FileAuditLogQueryService.parseLine: Line has {} fields (expected 10), " +
                    "likely due to unescaped pipes in message content", parts.length);
            // Join parts[7] through parts[parts.length-3] as the message
            StringBuilder messageBuilder = new StringBuilder(parts[7]);
            for (int i = 8; i < parts.length - 2; i++) {
                messageBuilder.append("|").append(parts[i]);
            }
            parts[7] = messageBuilder.toString();
            // Shift the last two fields (userAgent and extraData) to their expected positions
            parts[8] = parts[parts.length - 2];
            parts[9] = parts[parts.length - 1];
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

        // Try each formatter - first as ZonedDateTime, then as LocalDateTime
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            // Try parsing as ZonedDateTime (for formats with timezone info)
            try {
                ZonedDateTime zdt = ZonedDateTime.parse(dateStr.trim(), formatter);
                return zdt.toInstant();
            } catch (DateTimeParseException e) {
                // Try next approach
            }

            // Try parsing as LocalDateTime (for formats without timezone info like MessageFormat output)
            try {
                LocalDateTime ldt = LocalDateTime.parse(dateStr.trim(), formatter);
                return ldt.atZone(ZoneId.systemDefault()).toInstant();
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
