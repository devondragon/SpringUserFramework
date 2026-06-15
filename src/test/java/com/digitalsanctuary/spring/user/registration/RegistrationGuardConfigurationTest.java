package com.digitalsanctuary.spring.user.registration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;

/**
 * Verifies the wiring contract of {@link RegistrationGuardConfiguration}:
 *
 * <ul>
 *   <li>With no consumer guard, the permit-all {@link DefaultRegistrationGuard} is registered so the
 *       composite always has a delegate.</li>
 *   <li>The {@link CompositeRegistrationGuard} is always registered and is the {@code @Primary} bean, so
 *       single-valued {@link RegistrationGuard} injection points resolve to it.</li>
 *   <li>With one or more consumer guards, the default is NOT added and the composite delegates to the
 *       consumer guards in {@code @Order} with first-deny-wins semantics.</li>
 * </ul>
 */
@DisplayName("RegistrationGuardConfiguration Wiring Tests")
class RegistrationGuardConfigurationTest {

    private static final RegistrationContext CONTEXT =
            new RegistrationContext("user@example.com", RegistrationSource.FORM, null);

    // Register RegistrationGuardConfiguration as an auto-configuration so its @ConditionalOnMissingBean
    // evaluates AFTER any consumer-supplied guard beans — mirroring production, where this configuration
    // is component-scanned by the UserConfiguration auto-configuration (loaded after consumer beans).
    //
    // The inlined "spring.profiles.active" pins this context to a non-"test" profile so the @Profile("!test")
    // guard configs below always load. Without it, the test depends on no "test" profile being active — a
    // fragile assumption, because anything that sets the global "spring.profiles.active=test" system property
    // (e.g. another test running concurrently) would suppress the guard configs and flake these assertions.
    // The inlined property source outranks any leaked system property, making this deterministic.
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withPropertyValues("spring.profiles.active=registrationguardtest")
            .withConfiguration(AutoConfigurations.of(RegistrationGuardConfiguration.class));

    @Test
    @DisplayName("No consumer guard: default permit-all registered, composite is primary and allows")
    void noConsumerGuard() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(DefaultRegistrationGuard.class);
            assertThat(ctx).hasSingleBean(CompositeRegistrationGuard.class);
            // The primary RegistrationGuard injection point resolves to the composite.
            RegistrationGuard primary = ctx.getBean(RegistrationGuard.class);
            assertThat(primary).isInstanceOf(CompositeRegistrationGuard.class);
            assertThat(primary.evaluate(CONTEXT).allowed()).isTrue();
        });
    }

    @Test
    @DisplayName("One consumer guard: default NOT registered, composite delegates to the consumer guard")
    void oneConsumerGuard() {
        runner.withUserConfiguration(OneDenyingGuardConfig.class).run(ctx -> {
            assertThat(ctx).doesNotHaveBean(DefaultRegistrationGuard.class);
            assertThat(ctx).hasSingleBean(CompositeRegistrationGuard.class);

            RegistrationGuard primary = ctx.getBean(RegistrationGuard.class);
            assertThat(primary).isInstanceOf(CompositeRegistrationGuard.class);

            RegistrationDecision decision = primary.evaluate(CONTEXT);
            assertThat(decision.allowed()).isFalse();
            assertThat(decision.reason()).isEqualTo("consumer denied");
        });
    }

    @Test
    @DisplayName("Many consumer guards: ordered first-deny-wins; later guard not consulted")
    void manyConsumerGuardsFirstDenyWins() {
        runner.withUserConfiguration(TwoOrderedGuardsConfig.class).run(ctx -> {
            assertThat(ctx).doesNotHaveBean(DefaultRegistrationGuard.class);

            RegistrationGuard primary = ctx.getBean(RegistrationGuard.class);
            RegistrationDecision decision = primary.evaluate(CONTEXT);

            // The @Order(1) guard denies first and short-circuits the @Order(2) guard.
            assertThat(decision.allowed()).isFalse();
            assertThat(decision.reason()).isEqualTo("first");
        });
    }

    @Test
    @DisplayName("Many consumer guards all allowing: registration proceeds")
    void manyConsumerGuardsAllAllow() {
        runner.withUserConfiguration(TwoAllowingGuardsConfig.class).run(ctx -> {
            RegistrationGuard primary = ctx.getBean(RegistrationGuard.class);
            assertThat(primary.evaluate(CONTEXT).allowed()).isTrue();
        });
    }

    // These nested guard configurations are registered explicitly via ApplicationContextRunner
    // (withUserConfiguration). They are annotated @Profile("!test") so the library's broad
    // @ComponentScan("com.digitalsanctuary.spring.user") in UserConfiguration does NOT pick them up
    // as real beans inside the full @IntegrationTest context (which activates the "test" profile).
    // Without this, their denying RegistrationGuard beans would leak into every integration context's
    // CompositeRegistrationGuard and block all registration. The ApplicationContextRunner below
    // activates no profile, so "!test" is satisfied and these configs still load for these tests.
    @Configuration
    @Profile("!test")
    static class OneDenyingGuardConfig {
        @Bean
        RegistrationGuard consumerGuard() {
            return context -> RegistrationDecision.deny("consumer denied");
        }
    }

    @Configuration
    @Profile("!test")
    static class TwoOrderedGuardsConfig {
        @Bean
        @Order(1)
        RegistrationGuard firstGuard() {
            return context -> RegistrationDecision.deny("first");
        }

        @Bean
        @Order(2)
        RegistrationGuard secondGuard() {
            return context -> RegistrationDecision.deny("second");
        }
    }

    @Configuration
    @Profile("!test")
    static class TwoAllowingGuardsConfig {
        @Bean
        @Order(1)
        RegistrationGuard firstAllow() {
            return context -> RegistrationDecision.allow();
        }

        @Bean
        @Order(2)
        RegistrationGuard secondAllow() {
            return context -> RegistrationDecision.allow();
        }
    }
}
