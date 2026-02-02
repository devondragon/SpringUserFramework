package com.digitalsanctuary.spring.user.gdpr;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import com.digitalsanctuary.spring.user.audit.AuditEvent;
import com.digitalsanctuary.spring.user.audit.AuditEventDTO;
import com.digitalsanctuary.spring.user.audit.AuditLogQueryService;
import com.digitalsanctuary.spring.user.event.ConsentChangedEvent;
import com.digitalsanctuary.spring.user.persistence.model.User;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for tracking user consent via the audit system.
 *
 * <p>This service provides methods to record consent grants and withdrawals
 * using the existing audit infrastructure. Consent changes are stored as
 * audit events with specific action types:
 * <ul>
 *   <li>{@code CONSENT_GRANTED} - When consent is given</li>
 *   <li>{@code CONSENT_WITHDRAWN} - When consent is revoked</li>
 *   <li>{@code CONSENT_EXPIRED} - When consent expires (optional)</li>
 * </ul>
 *
 * <p>The consent details (type, version, method) are stored in the audit
 * event's extraData field as JSON.
 *
 * @see ConsentType
 * @see ConsentRecord
 * @see AuditEvent
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConsentAuditService {

    /** Action type for consent granted events. */
    public static final String ACTION_CONSENT_GRANTED = "CONSENT_GRANTED";

    /** Action type for consent withdrawn events. */
    public static final String ACTION_CONSENT_WITHDRAWN = "CONSENT_WITHDRAWN";

    /** Action type for consent expired events. */
    public static final String ACTION_CONSENT_EXPIRED = "CONSENT_EXPIRED";

    private final GdprConfig gdprConfig;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditLogQueryService auditLogQueryService;

    /**
     * Records that a user has granted consent.
     *
     * @param user the user granting consent
     * @param consentType the type of consent granted
     * @param policyVersion optional version of the policy document
     * @param method the method by which consent was given (e.g., "web_form", "api")
     * @param request the HTTP request (for IP and user agent)
     * @return the created consent record
     */
    public ConsentRecord recordConsentGranted(User user, ConsentType consentType, String policyVersion,
                                               String method, HttpServletRequest request) {
        return recordConsentGranted(user, consentType, null, policyVersion, method, request);
    }

    /**
     * Records that a user has granted consent, with support for custom consent types.
     *
     * @param user the user granting consent
     * @param consentType the type of consent granted
     * @param customType for CUSTOM consent type, the specific type name
     * @param policyVersion optional version of the policy document
     * @param method the method by which consent was given
     * @param request the HTTP request
     * @return the created consent record
     */
    public ConsentRecord recordConsentGranted(User user, ConsentType consentType, String customType,
                                               String policyVersion, String method, HttpServletRequest request) {
        if (!gdprConfig.isConsentTracking()) {
            log.debug("ConsentAuditService.recordConsentGranted: Consent tracking is disabled");
            return null;
        }

        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        if (consentType == null) {
            throw new IllegalArgumentException("Consent type cannot be null");
        }

        String ipAddress = getClientIP(request);
        String userAgent = request != null ? request.getHeader("User-Agent") : null;
        String sessionId = request != null ? request.getSession().getId() : null;

        ConsentRecord record = ConsentRecord.builder()
                .type(consentType)
                .customType(customType)
                .policyVersion(policyVersion)
                .grantedAt(Instant.now())
                .method(method)
                .ipAddress(ipAddress)
                .build();

        // Build extra data for audit log
        String extraData = buildExtraData(record);

        // Publish audit event
        AuditEvent auditEvent = AuditEvent.builder()
                .source(this)
                .user(user)
                .sessionId(sessionId)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .action(ACTION_CONSENT_GRANTED)
                .actionStatus("Success")
                .message("Consent granted: " + record.getEffectiveTypeName())
                .extraData(extraData)
                .build();

        eventPublisher.publishEvent(auditEvent);

        // Publish consent changed event
        eventPublisher.publishEvent(new ConsentChangedEvent(this, user, record,
                ConsentChangedEvent.ChangeType.GRANTED));

        log.info("ConsentAuditService.recordConsentGranted: Recorded consent grant for user {} - type {}",
                user.getEmail(), record.getEffectiveTypeName());

        return record;
    }

    /**
     * Records that a user has withdrawn consent.
     *
     * @param user the user withdrawing consent
     * @param consentType the type of consent being withdrawn
     * @param method the method by which consent was withdrawn
     * @param request the HTTP request
     * @return the created consent record
     */
    public ConsentRecord recordConsentWithdrawn(User user, ConsentType consentType,
                                                 String method, HttpServletRequest request) {
        return recordConsentWithdrawn(user, consentType, null, method, request);
    }

    /**
     * Records that a user has withdrawn consent, with support for custom consent types.
     *
     * @param user the user withdrawing consent
     * @param consentType the type of consent being withdrawn
     * @param customType for CUSTOM consent type, the specific type name
     * @param method the method by which consent was withdrawn
     * @param request the HTTP request
     * @return the created consent record
     */
    public ConsentRecord recordConsentWithdrawn(User user, ConsentType consentType, String customType,
                                                 String method, HttpServletRequest request) {
        if (!gdprConfig.isConsentTracking()) {
            log.debug("ConsentAuditService.recordConsentWithdrawn: Consent tracking is disabled");
            return null;
        }

        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        if (consentType == null) {
            throw new IllegalArgumentException("Consent type cannot be null");
        }

        String ipAddress = getClientIP(request);
        String userAgent = request != null ? request.getHeader("User-Agent") : null;
        String sessionId = request != null ? request.getSession().getId() : null;

        ConsentRecord record = ConsentRecord.builder()
                .type(consentType)
                .customType(customType)
                .withdrawnAt(Instant.now())
                .method(method)
                .ipAddress(ipAddress)
                .build();

        // Build extra data for audit log
        String extraData = buildExtraData(record);

        // Publish audit event
        AuditEvent auditEvent = AuditEvent.builder()
                .source(this)
                .user(user)
                .sessionId(sessionId)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .action(ACTION_CONSENT_WITHDRAWN)
                .actionStatus("Success")
                .message("Consent withdrawn: " + record.getEffectiveTypeName())
                .extraData(extraData)
                .build();

        eventPublisher.publishEvent(auditEvent);

        // Publish consent changed event
        eventPublisher.publishEvent(new ConsentChangedEvent(this, user, record,
                ConsentChangedEvent.ChangeType.WITHDRAWN));

        log.info("ConsentAuditService.recordConsentWithdrawn: Recorded consent withdrawal for user {} - type {}",
                user.getEmail(), record.getEffectiveTypeName());

        return record;
    }

    /**
     * Gets the current consent status for a user.
     *
     * <p>This method queries the audit log to determine which consents
     * are currently active (granted but not withdrawn) for the user.
     *
     * @param user the user to check
     * @return map of consent type names to their current status
     */
    public Map<String, ConsentStatus> getConsentStatus(User user) {
        if (user == null) {
            return new LinkedHashMap<>();
        }

        Map<String, ConsentStatus> statusMap = new LinkedHashMap<>();

        try {
            // Get all consent events
            List<AuditEventDTO> grantedEvents = auditLogQueryService.findByUserAndAction(user, ACTION_CONSENT_GRANTED);
            List<AuditEventDTO> withdrawnEvents = auditLogQueryService.findByUserAndAction(user, ACTION_CONSENT_WITHDRAWN);

            // Process grants (most recent first)
            for (AuditEventDTO event : grantedEvents) {
                String typeName = extractConsentType(event.getExtraData());
                if (typeName != null && !statusMap.containsKey(typeName)) {
                    statusMap.put(typeName, new ConsentStatus(typeName, true, event.getTimestamp(), null));
                }
            }

            // Process withdrawals
            for (AuditEventDTO event : withdrawnEvents) {
                String typeName = extractConsentType(event.getExtraData());
                if (typeName != null) {
                    ConsentStatus existing = statusMap.get(typeName);
                    if (existing != null && (existing.getWithdrawnAt() == null ||
                            event.getTimestamp().isAfter(existing.getGrantedAt()))) {
                        statusMap.put(typeName, new ConsentStatus(typeName, false,
                                existing.getGrantedAt(), event.getTimestamp()));
                    }
                }
            }

        } catch (Exception e) {
            log.warn("ConsentAuditService.getConsentStatus: Failed to get consent status for user {}: {}",
                    user.getEmail(), e.getMessage());
        }

        return statusMap;
    }

    /**
     * Gets all consent records for a user.
     *
     * @param user the user to get consents for
     * @return list of consent records
     */
    public List<ConsentRecord> getConsentRecords(User user) {
        if (user == null) {
            return new ArrayList<>();
        }

        List<ConsentRecord> records = new ArrayList<>();

        try {
            List<AuditEventDTO> allEvents = new ArrayList<>();
            allEvents.addAll(auditLogQueryService.findByUserAndAction(user, ACTION_CONSENT_GRANTED));
            allEvents.addAll(auditLogQueryService.findByUserAndAction(user, ACTION_CONSENT_WITHDRAWN));

            for (AuditEventDTO event : allEvents) {
                ConsentRecord record = parseConsentRecord(event);
                if (record != null) {
                    records.add(record);
                }
            }
        } catch (Exception e) {
            log.warn("ConsentAuditService.getConsentRecords: Failed to get consent records for user {}: {}",
                    user.getEmail(), e.getMessage());
        }

        return records;
    }

    /**
     * Builds the extraData JSON string for an audit event.
     * Uses simple string building to avoid Jackson dependency at compile time.
     */
    private String buildExtraData(ConsentRecord record) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"consentType\":\"").append(escapeJson(record.getEffectiveTypeName())).append("\"");
        if (record.getPolicyVersion() != null) {
            sb.append(",\"policyVersion\":\"").append(escapeJson(record.getPolicyVersion())).append("\"");
        }
        if (record.getMethod() != null) {
            sb.append(",\"method\":\"").append(escapeJson(record.getMethod())).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * Escapes special characters in a string for JSON.
     */
    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Extracts the consent type from audit event extra data.
     * Uses simple string parsing to avoid Jackson dependency at compile time.
     */
    private String extractConsentType(String extraData) {
        if (extraData == null || extraData.isEmpty()) {
            return null;
        }
        // Parse "consentType":"value" from JSON
        String key = "\"consentType\":\"";
        int start = extraData.indexOf(key);
        if (start == -1) {
            // Try without quotes around key
            key = "consentType\":\"";
            start = extraData.indexOf(key);
        }
        if (start == -1) {
            return null;
        }
        start += key.length();
        int end = extraData.indexOf("\"", start);
        if (end > start) {
            return extraData.substring(start, end);
        }
        return null;
    }

    /**
     * Parses a consent record from an audit event.
     */
    private ConsentRecord parseConsentRecord(AuditEventDTO event) {
        if (event == null) {
            return null;
        }

        String typeName = extractConsentType(event.getExtraData());
        if (typeName == null) {
            return null;
        }

        ConsentType type = ConsentType.fromValue(typeName);
        String customType = type == ConsentType.CUSTOM ? typeName : null;

        boolean isGrant = ACTION_CONSENT_GRANTED.equals(event.getAction());

        return ConsentRecord.builder()
                .type(type)
                .customType(customType)
                .grantedAt(isGrant ? event.getTimestamp() : null)
                .withdrawnAt(!isGrant ? event.getTimestamp() : null)
                .ipAddress(event.getIpAddress())
                .build();
    }

    /**
     * Gets the client IP address from the request.
     */
    private String getClientIP(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Represents the current status of a consent type.
     */
    public static class ConsentStatus {
        private final String consentType;
        private final boolean active;
        private final Instant grantedAt;
        private final Instant withdrawnAt;

        public ConsentStatus(String consentType, boolean active, Instant grantedAt, Instant withdrawnAt) {
            this.consentType = consentType;
            this.active = active;
            this.grantedAt = grantedAt;
            this.withdrawnAt = withdrawnAt;
        }

        public String getConsentType() {
            return consentType;
        }

        public boolean isActive() {
            return active;
        }

        public Instant getGrantedAt() {
            return grantedAt;
        }

        public Instant getWithdrawnAt() {
            return withdrawnAt;
        }
    }

}
