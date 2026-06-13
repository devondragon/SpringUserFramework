package com.digitalsanctuary.spring.user.registration;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import lombok.extern.slf4j.Slf4j;

/**
 * Auto-configuration for the {@link RegistrationGuard} SPI.
 *
 * <p>Two beans are registered:</p>
 * <ul>
 *   <li>A {@link DefaultRegistrationGuard} (permit-all) named {@code defaultRegistrationGuard}, created
 *       only when the consuming application defines no other "custom" {@link RegistrationGuard} bean.
 *       This guarantees the composite always has at least one delegate so registration is never
 *       silently un-guarded.</li>
 *   <li>A {@link Primary} {@link CompositeRegistrationGuard} that wraps every {@link RegistrationGuard}
 *       bean (the default, or one-or-more consumer guards) and evaluates them in order with
 *       first-deny-wins semantics. Because it is {@code @Primary}, all single-valued
 *       {@code RegistrationGuard} injection points (e.g. {@code UserService}) resolve to it.</li>
 * </ul>
 *
 * <p>The {@code List<RegistrationGuard>} injected into the composite factory method is populated by
 * Spring with all {@link RegistrationGuard} beans except the composite itself (which is still under
 * construction), ordered by {@code @Order}/{@link org.springframework.core.Ordered} where present.</p>
 */
@Slf4j
@Configuration
public class RegistrationGuardConfiguration {

    /**
     * Registers the permit-all {@link DefaultRegistrationGuard} when the consuming application supplies
     * no other {@link RegistrationGuard} bean.
     *
     * <p>The {@code @ConditionalOnMissingBean} explicitly {@code ignored}s the
     * {@link CompositeRegistrationGuard} declared in this same configuration. Without that, the
     * composite (which itself implements {@link RegistrationGuard}) could satisfy the condition and
     * suppress the default — leaving the composite with an empty delegate list when the consumer
     * provides no guards. By ignoring the composite, the default is created when (and only when) the
     * <em>consumer</em> provides no {@link RegistrationGuard}, guaranteeing the composite always has at
     * least one delegate.</p>
     *
     * @return a permit-all registration guard
     */
    @Bean
    @ConditionalOnMissingBean(value = RegistrationGuard.class, ignored = CompositeRegistrationGuard.class)
    public DefaultRegistrationGuard defaultRegistrationGuard() {
        log.info("No custom RegistrationGuard bean found — using DefaultRegistrationGuard (permit-all)");
        return new DefaultRegistrationGuard();
    }

    /**
     * Registers the primary {@link CompositeRegistrationGuard} that composes all available
     * {@link RegistrationGuard} delegates with first-deny-wins ordering.
     *
     * @param guards all {@link RegistrationGuard} beans (the default permit-all, or one-or-more consumer
     *               guards), ordered by {@code @Order}/{@link org.springframework.core.Ordered}
     * @return the primary composite registration guard
     */
    @Bean
    @Primary
    public CompositeRegistrationGuard compositeRegistrationGuard(final List<RegistrationGuard> guards) {
        log.info("Registering CompositeRegistrationGuard with {} delegate guard(s)", guards.size());
        return new CompositeRegistrationGuard(guards);
    }
}
