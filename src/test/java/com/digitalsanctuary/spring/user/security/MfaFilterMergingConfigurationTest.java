package com.digitalsanctuary.spring.user.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;

/**
 * Tests for {@link MfaFilterMergingConfiguration}.
 * <p>
 * Guards the most safety-critical MFA runtime behaviour: that {@code setMfaEnabled(true)} is applied to authentication
 * processing filters (without which a second factor REPLACES the first and the user can never satisfy all required
 * factors &mdash; the H4 lockout), and that this behaviour is correctly gated behind {@code user.mfa.enabled=true}. A
 * Spring Boot upgrade that changed {@code BeanPostProcessor} ordering, or a regression in the gating, would surface here.
 * </p>
 */
@DisplayName("MfaFilterMergingConfiguration Tests")
class MfaFilterMergingConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(MfaFilterMergingConfiguration.class);

    @Test
    @DisplayName("Post-processor enables MFA mode on authentication processing filters")
    void shouldEnableMfaOnProcessingFilter() {
        BeanPostProcessor postProcessor = MfaFilterMergingConfiguration.mfaFilterMergingPostProcessor();
        AbstractAuthenticationProcessingFilter filter = mock(AbstractAuthenticationProcessingFilter.class);

        Object result = postProcessor.postProcessAfterInitialization(filter, "someAuthenticationFilter");

        assertThat(result).isSameAs(filter);
        verify(filter).setMfaEnabled(true);
    }

    @Test
    @DisplayName("Post-processor leaves non-filter beans untouched")
    void shouldLeaveNonFilterBeansUntouched() {
        BeanPostProcessor postProcessor = MfaFilterMergingConfiguration.mfaFilterMergingPostProcessor();
        Object other = new Object();

        assertThat(postProcessor.postProcessAfterInitialization(other, "someOtherBean")).isSameAs(other);
    }

    @Test
    @DisplayName("Post-processor bean is registered ONLY when user.mfa.enabled=true")
    void shouldGatePostProcessorOnProperty() {
        runner.withPropertyValues("user.mfa.enabled=true")
                .run(context -> assertThat(context).hasNotFailed().hasBean("mfaFilterMergingPostProcessor"));
        runner.withPropertyValues("user.mfa.enabled=false")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean("mfaFilterMergingPostProcessor"));
        runner.run(context -> assertThat(context).hasNotFailed().doesNotHaveBean("mfaFilterMergingPostProcessor"));
    }
}
