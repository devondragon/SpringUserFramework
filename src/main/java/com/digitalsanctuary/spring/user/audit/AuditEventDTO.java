package com.digitalsanctuary.spring.user.audit;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object representing an audit event for query results.
 * Used by {@link AuditLogQueryService} to return audit event data
 * in a structured format, particularly for GDPR data export.
 *
 * @see AuditLogQueryService
 * @see AuditEvent
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEventDTO {

    /**
     * The timestamp when the audit event occurred.
     */
    private Instant timestamp;

    /**
     * The action that was performed (e.g., "Login", "Registration", "PasswordUpdate").
     */
    private String action;

    /**
     * The status of the action (e.g., "Success", "Failure").
     */
    private String actionStatus;

    /**
     * The user ID associated with the event.
     */
    private String userId;

    /**
     * The email address of the user associated with the event.
     */
    private String userEmail;

    /**
     * The IP address from which the action was performed.
     */
    private String ipAddress;

    /**
     * The session ID associated with the event.
     */
    private String sessionId;

    /**
     * A descriptive message about the event.
     */
    private String message;

    /**
     * The user agent string of the client.
     */
    private String userAgent;

    /**
     * Additional data associated with the event in JSON format.
     */
    private String extraData;

}
