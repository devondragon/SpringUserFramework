package com.digitalsanctuary.spring.user.gdpr;

/**
 * Enumeration of standard consent types for GDPR compliance.
 *
 * <p>These represent common categories of consent that applications
 * may need to track. Use {@link #CUSTOM} for application-specific
 * consent types not covered by the standard options.
 *
 * @see ConsentRecord
 * @see ConsentAuditService
 */
public enum ConsentType {

    /**
     * Consent to the terms of service.
     */
    TERMS_OF_SERVICE("terms_of_service"),

    /**
     * Consent to the privacy policy.
     */
    PRIVACY_POLICY("privacy_policy"),

    /**
     * Consent to receive marketing emails.
     */
    MARKETING_EMAILS("marketing_emails"),

    /**
     * Consent for data processing activities.
     */
    DATA_PROCESSING("data_processing"),

    /**
     * Consent to share data with third parties.
     */
    THIRD_PARTY_SHARING("third_party_sharing"),

    /**
     * Consent for analytics and tracking.
     */
    ANALYTICS("analytics"),

    /**
     * Custom consent type. Use {@link ConsentRecord#customType}
     * to specify the actual consent type name.
     */
    CUSTOM("custom");

    private final String value;

    ConsentType(String value) {
        this.value = value;
    }

    /**
     * Gets the string value of the consent type.
     *
     * @return the consent type value
     */
    public String getValue() {
        return value;
    }

    /**
     * Finds a ConsentType by its string value.
     *
     * @param value the string value to look up
     * @return the matching ConsentType, or CUSTOM if not found
     */
    public static ConsentType fromValue(String value) {
        if (value == null) {
            return CUSTOM;
        }
        for (ConsentType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        return CUSTOM;
    }

}
