package com.digitalsanctuary.spring.user;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Main auto-configuration class for the DigitalSanctuary Spring Boot User Framework Library.
 * Enables asynchronous processing, retry support, scheduling, method-level security,
 * and component scanning for the user framework package.
 *
 * @see UserAutoConfigurationRegistrar
 */
@Slf4j
@Configuration
@EnableAsync
@EnableRetry
@EnableScheduling
@EnableMethodSecurity
@ComponentScan(basePackages = "com.digitalsanctuary.spring.user")
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

}
