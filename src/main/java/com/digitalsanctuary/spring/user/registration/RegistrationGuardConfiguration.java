package com.digitalsanctuary.spring.user.registration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for the {@link RegistrationGuard} SPI.
 *
 * <p>Registers a {@link DefaultRegistrationGuard} (permit-all) when no custom
 * {@link RegistrationGuard} bean is defined by the consuming application.</p>
 */
@Configuration
public class RegistrationGuardConfiguration {

    @Bean
    @ConditionalOnMissingBean(RegistrationGuard.class)
    public RegistrationGuard registrationGuard() {
        return new DefaultRegistrationGuard();
    }
}
