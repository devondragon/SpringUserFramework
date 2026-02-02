package com.digitalsanctuary.spring.user.gdpr;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object representing a consent record.
 *
 * <p>Used to track user consent grants and withdrawals for GDPR compliance.
 * Consent records are stored in the audit log and can be queried for
 * export or compliance reporting.
 *
 * @see ConsentType
 * @see ConsentAuditService
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsentRecord {

    /**
     * The type of consent (e.g., PRIVACY_POLICY, MARKETING_EMAILS).
     */
    private ConsentType type;

    /**
     * For {@link ConsentType#CUSTOM}, specifies the custom consent type name.
     * Should be null for standard consent types.
     */
    private String customType;

    /**
     * Optional version identifier for the policy document.
     * (e.g., "privacy-policy-v1.2", "tos-2024-01").
     */
    private String policyVersion;

    /**
     * The timestamp when consent was granted.
     */
    private Instant grantedAt;

    /**
     * The timestamp when consent was withdrawn.
     * Null if consent is still active.
     */
    private Instant withdrawnAt;

    /**
     * The method by which consent was given or withdrawn.
     * Common values: "web_form", "api", "implicit", "checkbox".
     */
    private String method;

    /**
     * The IP address from which consent was given or withdrawn.
     */
    private String ipAddress;

    /**
     * Gets the effective consent type name.
     * For CUSTOM types, returns the customType value; otherwise returns the type's value.
     *
     * @return the effective consent type name
     */
    public String getEffectiveTypeName() {
        if (type == ConsentType.CUSTOM && customType != null) {
            return customType;
        }
        return type != null ? type.getValue() : null;
    }

    /**
     * Checks if this consent is currently active (granted and not withdrawn).
     *
     * @return true if consent is active
     */
    public boolean isActive() {
        return grantedAt != null && withdrawnAt == null;
    }

}
