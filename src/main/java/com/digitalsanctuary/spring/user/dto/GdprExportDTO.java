package com.digitalsanctuary.spring.user.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import com.digitalsanctuary.spring.user.audit.AuditEventDTO;
import com.digitalsanctuary.spring.user.gdpr.ConsentRecord;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object containing all exported user data for GDPR compliance.
 *
 * <p>This DTO aggregates all user data from the framework and any registered
 * {@link com.digitalsanctuary.spring.user.gdpr.GdprDataContributor} implementations.
 *
 * @see com.digitalsanctuary.spring.user.gdpr.GdprExportService
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GdprExportDTO {

    /**
     * Metadata about the export.
     */
    private ExportMetadata metadata;

    /**
     * Core user account data.
     */
    private UserData userData;

    /**
     * User's audit event history.
     */
    private List<AuditEventDTO> auditHistory;

    /**
     * User's consent records.
     */
    private List<ConsentRecord> consents;

    /**
     * Token metadata (existence and expiry, not actual token values).
     */
    private TokenMetadata tokens;

    /**
     * Data contributed by application-specific {@link com.digitalsanctuary.spring.user.gdpr.GdprDataContributor} implementations.
     * Map keys are the contributor's data keys.
     */
    private Map<String, Object> additionalData;

    /**
     * Metadata about the export operation.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExportMetadata {
        /**
         * Timestamp when the export was generated.
         */
        private Instant exportedAt;

        /**
         * Version of the export format.
         */
        private String formatVersion;

        /**
         * Name of the exporting service/application.
         */
        private String exportedBy;
    }

    /**
     * Core user account data.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserData {
        /**
         * User's unique identifier.
         */
        private Long id;

        /**
         * User's email address.
         */
        private String email;

        /**
         * User's first name.
         */
        private String firstName;

        /**
         * User's last name.
         */
        private String lastName;

        /**
         * Account registration date.
         */
        private Instant registrationDate;

        /**
         * Last activity timestamp.
         */
        private Instant lastActivityDate;

        /**
         * Whether the account is enabled.
         */
        private boolean enabled;

        /**
         * Whether the account is locked.
         */
        private boolean locked;

        /**
         * Authentication provider (LOCAL, GOOGLE, FACEBOOK, etc.).
         */
        private String provider;

        /**
         * User's assigned roles.
         */
        private List<String> roles;
    }

    /**
     * Token metadata (without revealing actual token values).
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TokenMetadata {
        /**
         * Whether a verification token exists.
         */
        private boolean hasVerificationToken;

        /**
         * Verification token expiry date, if exists.
         */
        private Instant verificationTokenExpiry;

        /**
         * Whether a password reset token exists.
         */
        private boolean hasPasswordResetToken;

        /**
         * Password reset token expiry date, if exists.
         */
        private Instant passwordResetTokenExpiry;
    }

}
