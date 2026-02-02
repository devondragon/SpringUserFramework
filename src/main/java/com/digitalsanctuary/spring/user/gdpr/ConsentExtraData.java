package com.digitalsanctuary.spring.user.gdpr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for consent extra data stored in audit events.
 *
 * <p>This class is used for Jackson serialization/deserialization of the
 * extraData field in consent-related audit events.
 *
 * @see ConsentAuditService
 * @see GdprExportService
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsentExtraData {

    /**
     * The consent type name (e.g., "MARKETING", "ANALYTICS", or custom type).
     */
    private String consentType;

    /**
     * Optional version of the policy document (e.g., "privacy-policy-v1.2").
     */
    private String policyVersion;

    /**
     * The method by which consent was given/withdrawn (e.g., "web_form", "api").
     */
    private String method;

}
