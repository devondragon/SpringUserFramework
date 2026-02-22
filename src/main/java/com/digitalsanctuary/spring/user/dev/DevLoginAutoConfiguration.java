package com.digitalsanctuary.spring.user.dev;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.PropertySource;

/**
 * Auto-configuration for the dev login feature.
 * <p>
 * Activates only when the {@code local} profile is active and
 * {@code user.dev.auto-login-enabled=true}. Individual dev-login components
 * ({@link DevLoginController}, {@link DevLoginStartupWarning}) carry their own
 * guards for defense-in-depth.
 * </p>
 */
@Configuration
@Profile("local")
@ConditionalOnProperty(name = "user.dev.auto-login-enabled", havingValue = "true", matchIfMissing = false)
@PropertySource("classpath:config/dsspringuserconfig.properties")
@EnableConfigurationProperties(DevLoginConfigProperties.class)
public class DevLoginAutoConfiguration {
}
