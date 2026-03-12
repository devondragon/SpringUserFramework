package com.digitalsanctuary.spring.user.registration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;

/**
 * Auto-configuration for the {@link RegistrationGuard} SPI.
 *
 * <p>Registers a {@link DefaultRegistrationGuard} (permit-all) when no custom
 * {@link RegistrationGuard} bean is defined by the consuming application.</p>
 */
@Slf4j
@Configuration
public class RegistrationGuardConfiguration {

    @Bean
    @ConditionalOnMissingBean(RegistrationGuard.class)
    public RegistrationGuard registrationGuard() {
        log.info("No custom RegistrationGuard bean found — using DefaultRegistrationGuard (permit-all)");
        return new DefaultRegistrationGuard();
    }
}
