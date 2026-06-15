package com.digitalsanctuary.spring.user.security;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import lombok.extern.slf4j.Slf4j;

/**
 * Activates Spring Security 7 factor merging by enabling MFA mode on authentication processing filters. This is the
 * "merging" half of multi-factor login described in {@link MfaConfiguration}; it is isolated here, in a small
 * configuration with <em>no</em> constructor-injected dependencies and no {@code @EventListener} methods, on purpose.
 *
 * <p>
 * The merging behaviour is provided by a {@code static} {@link BeanPostProcessor} {@code @Bean}. A {@code BeanPostProcessor}-
 * declaring class is instantiated very early in the context lifecycle (before the regular bean-instantiation phase). Keeping
 * that declaration on the dependency-rich {@link MfaConfiguration} would force <em>that</em> class to be instantiated early
 * too &mdash; which can emit the "not eligible for getting processed by all BeanPostProcessors" warning and interfere with
 * its {@code @EventListener} registration. Housing the post-processor in this dependency-free class avoids that entirely.
 * </p>
 *
 * <p>
 * <b>WARNING &mdash; scope of {@code setMfaEnabled(true)}.</b> When {@code user.mfa.enabled=true}, the post-processor flips
 * MFA mode on <em>every</em> {@link AbstractAuthenticationProcessingFilter} bean in the application context. That includes
 * any filter a <em>consuming application</em> defines that extends this base class (e.g. a custom JWT or API-key
 * authentication filter). Such a filter will then also perform SS7 factor merging: on a subsequent authentication for an
 * already-authenticated principal it rebuilds the result via {@code authenticationResult.toBuilder()...}. If that filter's
 * {@link org.springframework.security.core.Authentication} implementation does not support {@code toBuilder()}, the merge can
 * throw at runtime. Consumers enabling MFA who also register custom processing filters should be aware their filters are
 * affected. (This mirrors the framework default: it is only active when MFA is explicitly enabled.)
 * </p>
 *
 * @see MfaConfiguration
 * @see AbstractAuthenticationProcessingFilter#setMfaEnabled(boolean)
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "user.mfa.enabled", havingValue = "true", matchIfMissing = false)
public class MfaFilterMergingConfiguration {

    /**
     * Replicates the behaviour of {@code @EnableMultiFactorAuthentication}'s internal {@code EnableMfaFiltersPostProcessor}
     * using only public API, by invoking the public {@link AbstractAuthenticationProcessingFilter#setMfaEnabled(boolean)} on
     * every authentication processing filter. Without this, completing a second factor would REPLACE the first factor's
     * authentication (dropping its authority) and the user could never satisfy all required factors (the H4 lockout).
     *
     * <p>
     * Declared {@code static} so the post-processor can be registered without eagerly instantiating this configuration
     * class. The bean exists only when {@code user.mfa.enabled=true}, so the default (no-MFA) login path is unaffected.
     * </p>
     *
     * @return a {@link BeanPostProcessor} that calls {@code setMfaEnabled(true)} on authentication processing filters
     */
    @Bean
    public static BeanPostProcessor mfaFilterMergingPostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                // Intentionally scoped to AbstractAuthenticationProcessingFilter, which covers every authentication
                // mechanism this framework configures: formLogin, webAuthn, and oauth2Login all extend it. SS's internal
                // EnableMfaFiltersPostProcessor additionally flips the flag on AuthenticationFilter,
                // BasicAuthenticationFilter, and pre-authentication filters; this framework does not configure those
                // mechanisms, so they are deliberately not targeted here. See the class-level WARNING regarding
                // consumer-defined filters that extend this base class.
                if (bean instanceof AbstractAuthenticationProcessingFilter filter) {
                    filter.setMfaEnabled(true);
                    log.debug("MFA factor merging enabled on filter: {}", bean.getClass().getName());
                }
                return bean;
            }
        };
    }
}
