package com.digitalsanctuary.spring.user.dev;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;
import lombok.Data;

/**
 * Configuration properties for the dev login feature.
 * <p>
 * This enables a quick "login as" endpoint for local development, removing the need
 * for consuming applications to write boilerplate dev-login controllers.
 * </p>
 * <p>
 * <strong>SECURITY WARNING:</strong> This feature should only be enabled in local/dev
 * environments. It allows authentication without a password via a simple GET request.
 * </p>
 */
@Data
@Component
@PropertySource("classpath:config/dsspringuserconfig.properties")
@ConfigurationProperties(prefix = "user.dev")
public class DevLoginConfigProperties {

    /**
     * Whether the dev auto-login feature is enabled. Defaults to false.
     * Must be explicitly set to true AND the "local" profile must be active.
     */
    private boolean autoLoginEnabled = false;

    /**
     * The URL to redirect to after a successful dev login. Defaults to "/".
     */
    private String loginRedirectUrl = "/";
}
