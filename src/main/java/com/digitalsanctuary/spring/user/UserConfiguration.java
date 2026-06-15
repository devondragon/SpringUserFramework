package com.digitalsanctuary.spring.user;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Main auto-configuration class for the DigitalSanctuary Spring Boot User Framework Library.
 * Provides component scanning for the user framework package and enables, by default, asynchronous
 * processing, retry support, scheduling, and method-level security.
 *
 * <p>
 * Each cross-cutting enabler is gated behind its own opt-out property (all default {@code true}, so
 * behavior is unchanged unless explicitly disabled). A consuming application that already manages one
 * of these concerns globally can disable the library's copy to avoid double-activation conflicts:
 * <ul>
 * <li>{@code user.async.enabled} &rarr; {@code @EnableAsync}</li>
 * <li>{@code user.retry.enabled} &rarr; {@code @EnableRetry}</li>
 * <li>{@code user.scheduling.enabled} &rarr; {@code @EnableScheduling}</li>
 * <li>{@code user.method-security.enabled} &rarr; {@code @EnableMethodSecurity}</li>
 * </ul>
 *
 * @see UserAutoConfigurationRegistrar
 */
@Slf4j
@AutoConfiguration
@ComponentScan(basePackages = "com.digitalsanctuary.spring.user",
        excludeFilters = {@ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
                @ComponentScan.Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class)})
@Import(UserAutoConfigurationRegistrar.class)
public class UserConfiguration {


    /**
     * Logs a message when the UserConfiguration class is loaded to indicate that the DigitalSanctuary Spring Boot User Framework Library has been
     * loaded.
     */
    @PostConstruct
    public void onStartup() {
        log.info("DigitalSanctuary SpringBoot User Framework Library loaded.");
    }

    /**
     * Enables Spring's asynchronous method execution support for the library. Gated behind
     * {@code user.async.enabled} (default {@code true}). Disable if the consuming application already
     * enables async processing globally to avoid double-activation.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "user.async.enabled", havingValue = "true", matchIfMissing = true)
    @EnableAsync
    static class AsyncConfiguration {
    }

    /**
     * Enables Spring Retry support for the library. Gated behind {@code user.retry.enabled}
     * (default {@code true}). Disable if the consuming application already enables retry globally to
     * avoid double-activation.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "user.retry.enabled", havingValue = "true", matchIfMissing = true)
    @EnableRetry
    static class RetryConfiguration {
    }

    /**
     * Enables Spring's scheduled task execution for the library (used by token purge and similar jobs).
     * Gated behind {@code user.scheduling.enabled} (default {@code true}). Disable if the consuming
     * application already enables scheduling globally to avoid double-activation.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "user.scheduling.enabled", havingValue = "true", matchIfMissing = true)
    @EnableScheduling
    static class SchedulingConfiguration {
    }

    /**
     * Enables Spring Security method-level security for the library. Gated behind
     * {@code user.method-security.enabled} (default {@code true}). Disable if the consuming application
     * already enables method security globally to avoid double-activation.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "user.method-security.enabled", havingValue = "true", matchIfMissing = true)
    @EnableMethodSecurity
    static class MethodSecurityConfiguration {
    }

}
