package com.digitalsanctuary.spring.user.registration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
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

    @Configuration
    static class OneDenyingGuardConfig {
        @Bean
        RegistrationGuard consumerGuard() {
            return context -> RegistrationDecision.deny("consumer denied");
        }
    }

    @Configuration
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
