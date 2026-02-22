package com.digitalsanctuary.spring.user.dev;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Configuration that registers {@link DevLoginConfigProperties}.
 * <p>
 * Activates only when the {@code local} profile is active and
 * {@code user.dev.auto-login-enabled=true}. Individual dev-login components
 * ({@link DevLoginController}, {@link DevLoginStartupWarning}) carry their own
 * guards for defense-in-depth.
 * </p>
 * <p>
 * Note: this class is registered via component scan (not Spring Boot SPI), so
 * default property values are provided by {@code WebAuthnConfiguration}, which
 * unconditionally loads {@code classpath:config/dsspringuserconfig.properties}.
 * </p>
 */
@Configuration
@Profile("local")
@ConditionalOnProperty(name = "user.dev.auto-login-enabled", havingValue = "true", matchIfMissing = false)
@EnableConfigurationProperties(DevLoginConfigProperties.class)
public class DevLoginConfiguration {
}
