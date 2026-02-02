package com.digitalsanctuary.spring.user.gdpr;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import com.digitalsanctuary.spring.user.audit.AuditEventDTO;
import com.digitalsanctuary.spring.user.audit.AuditLogQueryService;
import com.digitalsanctuary.spring.user.dto.GdprExportDTO;
import com.digitalsanctuary.spring.user.event.UserDataExportedEvent;
import com.digitalsanctuary.spring.user.persistence.model.PasswordResetToken;
import com.digitalsanctuary.spring.user.persistence.model.Role;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.model.VerificationToken;
import com.digitalsanctuary.spring.user.persistence.repository.PasswordResetTokenRepository;
import com.digitalsanctuary.spring.user.persistence.repository.VerificationTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Service for exporting user data in GDPR-compliant format.
 *
 * <p>This service aggregates all user data from the framework and any registered
 * {@link GdprDataContributor} implementations to create a comprehensive data export
 * as required by GDPR Article 15 (Right of Access) and Article 20 (Right to Data Portability).
 *
 * <p>The export includes:
 * <ul>
 *   <li>Core user account data (name, email, registration date, etc.)</li>
 *   <li>Audit history for the user</li>
 *   <li>Consent records</li>
 *   <li>Token metadata (not actual tokens)</li>
 *   <li>Data from registered {@link GdprDataContributor} beans</li>
 * </ul>
 *
 * @see GdprDataContributor
 * @see GdprExportDTO
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GdprExportService {

    private static final String EXPORT_FORMAT_VERSION = "1.0";
    private static final String EXPORTER_NAME = "Spring User Framework";

    private final GdprConfig gdprConfig;
    private final AuditLogQueryService auditLogQueryService;
    private final VerificationTokenRepository verificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final List<GdprDataContributor> dataContributors;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    /**
     * Exports all GDPR-relevant data for a user.
     *
     * @param user the user whose data to export
     * @return the complete GDPR export DTO
     * @throws IllegalArgumentException if user is null
     */
    public GdprExportDTO exportUserData(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }

        log.info("GdprExportService.exportUserData: Starting export for user {}", user.getId());

        GdprExportDTO export = GdprExportDTO.builder()
                .metadata(buildMetadata())
                .userData(buildUserData(user))
                .auditHistory(exportAuditHistory(user))
                .consents(exportConsents(user))
                .tokens(exportTokenMetadata(user))
                .additionalData(exportContributorData(user))
                .build();

        // Publish event after successful export
        eventPublisher.publishEvent(new UserDataExportedEvent(this, user, export));

        log.info("GdprExportService.exportUserData: Completed export for user {}", user.getId());
        return export;
    }

    /**
     * Builds export metadata.
     */
    private GdprExportDTO.ExportMetadata buildMetadata() {
        return GdprExportDTO.ExportMetadata.builder()
                .exportedAt(Instant.now())
                .formatVersion(EXPORT_FORMAT_VERSION)
                .exportedBy(EXPORTER_NAME)
                .build();
    }

    /**
     * Builds the user data section.
     */
    private GdprExportDTO.UserData buildUserData(User user) {
        List<String> roleNames = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toList());

        return GdprExportDTO.UserData.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .registrationDate(user.getRegistrationDate() != null
                        ? user.getRegistrationDate().toInstant() : null)
                .lastActivityDate(user.getLastActivityDate() != null
                        ? user.getLastActivityDate().toInstant() : null)
                .enabled(user.isEnabled())
                .locked(user.isLocked())
                .provider(user.getProvider() != null ? user.getProvider().name() : null)
                .roles(roleNames)
                .build();
    }

    /**
     * Exports the user's audit history.
     */
    private List<AuditEventDTO> exportAuditHistory(User user) {
        try {
            return auditLogQueryService.findByUser(user);
        } catch (Exception e) {
            log.warn("GdprExportService.exportAuditHistory: Failed to export audit history for user {}: {}",
                    user.getId(), e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Exports the user's consent records from audit logs.
     * Fetches all consent events once and processes together for efficiency.
     */
    private List<ConsentRecord> exportConsents(User user) {
        if (!gdprConfig.isConsentTracking()) {
            return new ArrayList<>();
        }

        try {
            // Fetch all consent-related audit events
            List<AuditEventDTO> grantedEvents = auditLogQueryService.findByUserAndAction(user, "CONSENT_GRANTED");
            List<AuditEventDTO> withdrawnEvents = auditLogQueryService.findByUserAndAction(user, "CONSENT_WITHDRAWN");

            // Combine and sort by timestamp (oldest first for chronological processing)
            List<AuditEventDTO> allEvents = new ArrayList<>(grantedEvents.size() + withdrawnEvents.size());
            allEvents.addAll(grantedEvents);
            allEvents.addAll(withdrawnEvents);
            allEvents.sort(Comparator.comparing(AuditEventDTO::getTimestamp,
                    Comparator.nullsFirst(Comparator.naturalOrder())));

            // Build consent records from audit events (process in chronological order)
            Map<String, ConsentRecord> consentMap = new LinkedHashMap<>();

            for (AuditEventDTO event : allEvents) {
                boolean isGrant = "CONSENT_GRANTED".equals(event.getAction());
                ConsentRecord record = parseConsentFromAuditEvent(event, isGrant);
                if (record == null) {
                    continue;
                }

                String key = record.getEffectiveTypeName();
                ConsentRecord existing = consentMap.get(key);

                if (isGrant) {
                    // Grant: create new or update existing
                    if (existing != null) {
                        existing.setGrantedAt(record.getGrantedAt());
                        existing.setPolicyVersion(record.getPolicyVersion());
                        existing.setMethod(record.getMethod());
                    } else {
                        consentMap.put(key, record);
                    }
                } else {
                    // Withdrawal: update existing or create new
                    if (existing != null) {
                        existing.setWithdrawnAt(record.getWithdrawnAt());
                    } else {
                        consentMap.put(key, record);
                    }
                }
            }

            return new ArrayList<>(consentMap.values());
        } catch (Exception e) {
            log.warn("GdprExportService.exportConsents: Failed to export consents for user {}: {}",
                    user.getId(), e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Parses a consent record from an audit event using Jackson deserialization.
     */
    private ConsentRecord parseConsentFromAuditEvent(AuditEventDTO event, boolean isGrant) {
        if (event == null || event.getExtraData() == null) {
            return null;
        }

        try {
            ConsentExtraData extraData = objectMapper.readValue(event.getExtraData(), ConsentExtraData.class);

            String typeValue = extraData.getConsentType();
            ConsentType type = ConsentType.fromValue(typeValue);
            String customType = (type == ConsentType.CUSTOM) ? typeValue : null;

            ConsentRecord.ConsentRecordBuilder builder = ConsentRecord.builder()
                    .type(type)
                    .customType(customType)
                    .policyVersion(extraData.getPolicyVersion())
                    .method(extraData.getMethod())
                    .ipAddress(event.getIpAddress());

            if (isGrant) {
                builder.grantedAt(event.getTimestamp());
            } else {
                builder.withdrawnAt(event.getTimestamp());
            }

            return builder.build();
        } catch (JacksonException e) {
            log.debug("GdprExportService.parseConsentFromAuditEvent: Failed to parse consent from event", e);
            return null;
        }
    }

    /**
     * Exports token metadata (existence and expiry, not actual tokens).
     */
    private GdprExportDTO.TokenMetadata exportTokenMetadata(User user) {
        VerificationToken verificationToken = verificationTokenRepository.findByUser(user);
        PasswordResetToken passwordResetToken = passwordResetTokenRepository.findByUser(user);

        return GdprExportDTO.TokenMetadata.builder()
                .hasVerificationToken(verificationToken != null)
                .verificationTokenExpiry(verificationToken != null && verificationToken.getExpiryDate() != null
                        ? verificationToken.getExpiryDate().toInstant() : null)
                .hasPasswordResetToken(passwordResetToken != null)
                .passwordResetTokenExpiry(passwordResetToken != null && passwordResetToken.getExpiryDate() != null
                        ? passwordResetToken.getExpiryDate().toInstant() : null)
                .build();
    }

    /**
     * Exports data from all registered GdprDataContributor beans.
     */
    private Map<String, Object> exportContributorData(User user) {
        Map<String, Object> additionalData = new LinkedHashMap<>();

        if (dataContributors == null || dataContributors.isEmpty()) {
            return additionalData;
        }

        for (GdprDataContributor contributor : dataContributors) {
            try {
                String key = contributor.getDataKey();
                Map<String, Object> data = contributor.exportUserData(user);
                if (data != null && !data.isEmpty()) {
                    additionalData.put(key, data);
                    log.debug("GdprExportService.exportContributorData: Added data from contributor '{}'", key);
                }
            } catch (Exception e) {
                log.warn("GdprExportService.exportContributorData: Contributor {} failed: {}",
                        contributor.getDataKey(), e.getMessage());
            }
        }

        return additionalData;
    }

}
