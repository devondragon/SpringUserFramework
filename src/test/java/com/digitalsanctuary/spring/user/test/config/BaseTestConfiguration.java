package com.digitalsanctuary.spring.user.test.config;

import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;

/**
 * Base test configuration providing common beans and mocks for all tests.
 * This configuration is automatically imported by test annotations and provides
 * a consistent testing environment.
 */
@TestConfiguration
@Profile("test")
public class BaseTestConfiguration {

    /**
     * Test password encoder using BCrypt with strength 4 for faster tests.
     */
    @Bean
    @Primary
    public PasswordEncoder testPasswordEncoder() {
        return new BCryptPasswordEncoder(4); // Lower strength for faster tests
    }

    /**
     * Test session registry for security testing.
     */
    @Bean
    @Primary
    public SessionRegistry testSessionRegistry() {
        return new SessionRegistryImpl();
    }

    /**
     * Mock JavaMailSender to prevent actual email sending during tests.
     * This is configured in MockMailConfiguration instead.
     */

    /**
     * Test event publisher that can be used to verify event publication.
     */
    @Bean
    @Primary
    public ApplicationEventPublisher testEventPublisher() {
        return Mockito.spy(ApplicationEventPublisher.class);
    }

    /**
     * Fixed clock for time-based testing.
     * Set to a known time for predictable test results.
     */
    @Bean
    @Primary
    public Clock testClock() {
        // Fixed time: 2024-01-15 10:00:00 UTC
        ZonedDateTime fixedTime = ZonedDateTime.of(
                LocalDateTime.of(2024, 1, 15, 10, 0, 0),
                ZoneId.of("UTC")
        );
        return Clock.fixed(fixedTime.toInstant(), ZoneId.of("UTC"));
    }

    /**
     * Default locale for consistent test results.
     */
    @Bean
    @Primary
    public Locale testLocale() {
        return Locale.US;
    }

    /**
     * Test-specific property overrides.
     */
    @Bean
    public TestPropertySourcesConfigurer testPropertySourcesConfigurer() {
        return new TestPropertySourcesConfigurer();
    }

    /**
     * Helper class to configure test properties programmatically.
     */
    public static class TestPropertySourcesConfigurer {
        
        public TestPropertySourcesConfigurer() {
            // Set system properties for tests
            System.setProperty("spring.profiles.active", "test");
            System.setProperty("spring.jpa.hibernate.ddl-auto", "create-drop");
            System.setProperty("spring.datasource.initialization-mode", "always");
        }
    }
}