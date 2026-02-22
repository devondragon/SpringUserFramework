package com.digitalsanctuary.spring.user.dev;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Logs a prominent warning on startup when the dev login feature is active.
 * This ensures developers are aware that passwordless authentication is enabled.
 */
@Slf4j
@Component
@Profile("local")
@ConditionalOnProperty(name = "user.dev.auto-login-enabled", havingValue = "true", matchIfMissing = false)
public class DevLoginStartupWarning {

    @PostConstruct
    public void logWarning() {
        log.warn("========================================================");
        log.warn("  DEV LOGIN IS ACTIVE");
        log.warn("  Passwordless authentication is enabled at /dev/login-as/{{email}}");
        log.warn("  DO NOT enable this in production!");
        log.warn("========================================================");
    }
}
