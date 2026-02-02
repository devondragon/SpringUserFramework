package com.digitalsanctuary.spring.user.gdpr;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;
import lombok.Data;

/**
 * Configuration properties for GDPR functionality.
 *
 * <p>Properties are bound from the {@code user.gdpr.*} prefix in application
 * configuration. All GDPR features can be enabled or disabled via these properties.
 *
 * @see GdprExportService
 * @see GdprDeletionService
 * @see ConsentAuditService
 */
@Data
@Component
@PropertySource("classpath:config/dsspringuserconfig.properties")
@ConfigurationProperties(prefix = "user.gdpr")
public class GdprConfig {

    /**
     * Master toggle for GDPR features. When disabled, GDPR endpoints
     * will return 404 Not Found responses.
     * Default: true
     */
    private boolean enabled = true;

    /**
     * If true, user data is automatically exported before hard deletion.
     * The export is returned in the deletion response for the user to save.
     * Default: true
     */
    private boolean exportBeforeDeletion = true;

    /**
     * If true, consent changes (grant/withdraw) are tracked via the audit system.
     * Default: true
     */
    private boolean consentTracking = true;

}
