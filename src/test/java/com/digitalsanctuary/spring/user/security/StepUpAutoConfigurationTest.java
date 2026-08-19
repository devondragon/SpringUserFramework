package com.digitalsanctuary.spring.user.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.digitalsanctuary.spring.user.roles.RolesAndPrivilegesConfig;

/**
 * Tests for {@link StepUpAutoConfiguration}.
 * <p>
 * The default-off contract matters as much as the feature itself: an existing deployment that upgrades must see no
 * {@code StepUpService} bean, since one appearing would start gating {@code setPassword} and passkey delete/rename.
 * </p>
 */
@DisplayName("StepUpAutoConfiguration Tests")
class StepUpAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(StepUpAutoConfiguration.class))
            .withBean(RolesAndPrivilegesConfig.class, RolesAndPrivilegesConfig::new)
            .withBean(MfaConfigProperties.class, MfaConfigProperties::new);

    @Test
    @DisplayName("should register no step-up service by default")
    void shouldRegisterNoServiceByDefault() {
        runner.run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(StepUpService.class));
    }

    @Test
    @DisplayName("should register no step-up service when explicitly disabled")
    void shouldRegisterNoServiceWhenDisabled() {
        runner.withPropertyValues("user.security.step-up.enabled=false")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(StepUpService.class));
    }

    @Test
    @DisplayName("should register the built-in service when enabled")
    void shouldRegisterBuiltInServiceWhenEnabled() {
        runner.withPropertyValues("user.security.step-up.enabled=true").run(context -> assertThat(context).hasNotFailed()
                .getBean(StepUpService.class).isInstanceOf(DSFactorFreshnessStepUpService.class));
    }

    @Test
    @DisplayName("should bind the camelCase property spelling used in the documentation")
    void shouldBindCamelCasePropertySpelling() {
        runner.withPropertyValues("user.security.stepUp.enabled=true", "user.security.stepUp.ttlSeconds=300")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(StepUpService.class);
                    assertThat(context.getBean(StepUpConfigProperties.class).getTtlSeconds()).isEqualTo(300);
                });
    }

    @Test
    @DisplayName("should back off when the application supplies its own step-up service")
    void shouldBackOffForConsumerSuppliedService() {
        runner.withPropertyValues("user.security.step-up.enabled=true").withUserConfiguration(ConsumerStepUpConfiguration.class)
                .run(context -> assertThat(context).hasNotFailed().getBean(StepUpService.class)
                        .isNotInstanceOf(DSFactorFreshnessStepUpService.class));
    }

    @Test
    @DisplayName("should fail startup when a configured factor name is unknown")
    void shouldFailStartupForUnknownFactor() {
        runner.withPropertyValues("user.security.step-up.enabled=true", "user.security.step-up.factors=WEBAUTHN,TELEPATHY")
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .hasMessageContaining("TELEPATHY").hasMessageContaining("Valid values"));
    }

    @Test
    @DisplayName("should fail startup when no factors are configured")
    void shouldFailStartupForEmptyFactors() {
        runner.withPropertyValues("user.security.step-up.enabled=true", "user.security.step-up.factors=")
                .run(context -> assertThat(context).hasFailed().getFailure().hasMessageContaining("no factors are configured"));
    }

    @Test
    @DisplayName("should register the factor-authority name validator regardless of whether step-up is enabled")
    void shouldAlwaysRegisterAuthorityNameValidator() {
        runner.run(context -> assertThat(context).hasNotFailed().hasSingleBean(FactorAuthorityNameValidator.class));
        runner.withPropertyValues("user.security.step-up.enabled=true")
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(FactorAuthorityNameValidator.class));
    }

    @Configuration(proxyBeanMethods = false)
    static class ConsumerStepUpConfiguration {

        @Bean
        StepUpService consumerStepUpService() {
            return mock(StepUpService.class);
        }
    }
}
