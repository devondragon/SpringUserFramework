package com.digitalsanctuary.spring.user.audit;

import java.time.Instant;
import java.util.List;
import com.digitalsanctuary.spring.user.persistence.model.User;

/**
 * Service interface for querying audit log entries.
 *
 * <p>This interface provides a pluggable abstraction for querying audit events,
 * allowing different implementations based on the audit storage backend
 * (file-based, database, Elasticsearch, etc.).
 *
 * <p>The default implementation {@link FileAuditLogQueryService} parses the
 * pipe-delimited log file created by {@link FileAuditLogWriter}. Applications
 * requiring more efficient queries for large volumes can provide their own
 * implementation backed by a database or log aggregation system.
 *
 * <p>Primary use case is GDPR data export, where all audit events for a user
 * must be retrievable.
 *
 * @see FileAuditLogQueryService
 * @see AuditEventDTO
 */
public interface AuditLogQueryService {

    /**
     * Find all audit events for a specific user.
     *
     * @param user the user whose audit events to retrieve
     * @return a list of audit events for the user, ordered by timestamp descending;
     *         empty list if no events found
     */
    List<AuditEventDTO> findByUser(User user);

    /**
     * Find audit events for a user since a given timestamp.
     *
     * @param user the user whose audit events to retrieve
     * @param since only return events after this timestamp
     * @return a list of audit events for the user since the given timestamp,
     *         ordered by timestamp descending; empty list if no events found
     */
    List<AuditEventDTO> findByUserSince(User user, Instant since);

    /**
     * Find audit events for a user filtered by action type.
     *
     * @param user the user whose audit events to retrieve
     * @param action the action type to filter by (e.g., "CONSENT_GRANTED", "Login")
     * @return a list of audit events matching the action type,
     *         ordered by timestamp descending; empty list if no events found
     */
    List<AuditEventDTO> findByUserAndAction(User user, String action);

}
