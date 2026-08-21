package com.digitalsanctuary.spring.user.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Locale;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.roles.RolesAndPrivilegesConfig;
import com.digitalsanctuary.spring.user.service.WebAuthnCredentialManagementService;

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
            .withBean(MfaConfigProperties.class, MfaConfigProperties::new)
            .withBean(WebAuthnConfigProperties.class, WebAuthnConfigProperties::new)
            // Set as a property, not on the instance: @ConfigurationProperties beans are re-bound after
            // construction, so a value set in the supplier is overwritten by dsspringuserconfig.properties.
            .withPropertyValues("user.webauthn.enabled=true");

    @Test
    @DisplayName("should fail startup when WEBAUTHN step-up is configured but WebAuthn is disabled")
    void shouldFailWhenWebAuthnFactorConfiguredButWebAuthnDisabled() {
        // Mirrors MfaConfiguration: requiring a factor the deployment cannot issue makes the gate unsatisfiable,
        // which would silently render step-up inert rather than enforcing it.
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(StepUpAutoConfiguration.class))
                .withBean(RolesAndPrivilegesConfig.class, RolesAndPrivilegesConfig::new)
                .withBean(MfaConfigProperties.class, MfaConfigProperties::new)
                .withBean(WebAuthnConfigProperties.class, WebAuthnConfigProperties::new)
                .withPropertyValues("user.security.step-up.enabled=true")
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .hasMessageContaining("user.webauthn.enabled=false"));
    }

    @Test
    @DisplayName("should fail startup when the step-up TTL is not positive")
    void shouldFailStartupForNonPositiveTtl() {
        // A zero or negative TTL makes every factor instantly stale, so every gated operation denies forever with a
        // debug log as the only signal. @Min(1) only runs because the class is @Validated.
        runner.withPropertyValues("user.security.step-up.enabled=true", "user.security.step-up.ttlSeconds=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("should fail startup when the enrollment TTL is not positive")
    void shouldFailStartupForNonPositiveEnrollmentTtl() {
        runner.withPropertyValues("user.security.step-up.enabled=true", "user.security.step-up.enrollmentTtlSeconds=-1")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("should answer satisfiability from the WebAuthn credential service when one is present")
    void shouldDelegateSatisfiabilityToTheCredentialService() {
        // Pins the auto-configuration's lambda: the built-in service must read real credential state, not a constant.
        WebAuthnCredentialManagementService credentialService = mock(WebAuthnCredentialManagementService.class);
        // Distinct ids: User equality keys on id alone, so two unsaved instances compare equal and Mockito could
        // not tell the stubs apart. See the note on User.id and EntityEqualityTest.
        User withPasskey = new User();
        withPasskey.setId(1L);
        User withoutPasskey = new User();
        withoutPasskey.setId(2L);
        when(credentialService.hasCredentials(withPasskey)).thenReturn(true);
        when(credentialService.hasCredentials(withoutPasskey)).thenReturn(false);

        runner.withBean(WebAuthnCredentialManagementService.class, () -> credentialService)
                .withPropertyValues("user.security.step-up.enabled=true").run(context -> {
                    StepUpService service = context.getBean(StepUpService.class);
                    assertThat(service.canSatisfyStepUp(withPasskey, "set-password")).isTrue();
                    assertThat(service.canSatisfyStepUp(withoutPasskey, "set-password")).isFalse();
                });
    }

    @Test
    @DisplayName("should report step-up unsatisfiable when no WebAuthn credential service is available")
    void shouldReportUnsatisfiableWhenCredentialServiceAbsent() {
        // Without the service no account can hold a passkey, so WEBAUTHN is unachievable and step-up does not
        // apply. Claiming otherwise would gate an operation nobody could ever unlock.
        runner.withPropertyValues("user.security.step-up.enabled=true").run(context -> assertThat(
                context.getBean(StepUpService.class).canSatisfyStepUp(new User(), "set-password")).isFalse());
    }

    @Test
    @DisplayName("should default canSatisfyStepUp to true for implementations that do not override it")
    void shouldDefaultSatisfiabilityToTrue() {
        // The compatibility promise in MIGRATION.md: a consumer SPI implementation written before this method
        // existed keeps being enforced, rather than silently opting out of step-up.
        StepUpService legacyImplementation = (user, action, request) -> false;

        assertThat(legacyImplementation.canSatisfyStepUp(new User(), "set-password")).isTrue();
    }

    @Test
    @ResourceLock(Resources.LOCALE)
    @DisplayName("should normalize factor names independently of the default locale")
    void shouldNormalizeFactorNamesIndependentlyOfDefaultLocale() {
        Locale original = Locale.getDefault();
        try {
            // In Turkish, "authorization_code".toUpperCase() dots the I and no longer matches the factor map.
            Locale.setDefault(Locale.forLanguageTag("tr"));
            runner.withPropertyValues("user.security.step-up.enabled=true",
                    "user.security.step-up.factors=authorization_code")
                    .run(context -> assertThat(context).hasNotFailed().hasSingleBean(StepUpService.class));
        } finally {
            Locale.setDefault(original);
        }
    }

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
