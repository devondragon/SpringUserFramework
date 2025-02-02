package com.digitalsanctuary.spring.user;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * The UserConfiguration class is a Spring Boot configuration class that provides configuration for the DigitalSanctuary Spring Boot User Framework
 * Library. This class is used to configure the user framework library, including enabling asynchronous processing and scheduling, and scanning for
 * components and repositories.
 */
@Slf4j
@Configuration
@EnableAsync
@EnableScheduling
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
