package com.digitalsanctuary.spring.user.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

/**
 * Auto-configuration that registers {@link WebAuthnConfigProperties}.
 * <p>
 * This configuration is always active because {@code WebSecurityConfig} requires
 * {@link WebAuthnConfigProperties} regardless of whether WebAuthn is enabled.
 * Individual WebAuthn components carry their own
 * {@code @ConditionalOnProperty(name = "user.webauthn.enabled")} guards.
 * </p>
 */
@Configuration
@PropertySource("classpath:config/dsspringuserconfig.properties")
@EnableConfigurationProperties(WebAuthnConfigProperties.class)
public class WebAuthnAutoConfiguration {
}
