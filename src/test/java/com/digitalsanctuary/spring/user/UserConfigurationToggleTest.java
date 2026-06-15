package com.digitalsanctuary.spring.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Verifies that the cross-cutting {@code @Enable*} support enabled by {@link UserConfiguration} is
 * individually toggleable and defaults to on (backward compatible).
 *
 * <p>
 * The four nested gating configurations are registered directly (rather than the full scanning
 * {@link UserConfiguration} entry point) so their {@code @ConditionalOnProperty(matchIfMissing = true)}
 * gates can be exercised in isolation. Registering the full {@code UserConfiguration} here would trigger
 * its broad {@code @ComponentScan("com.digitalsanctuary.spring.user")}, which in the test classpath also
 * picks up unrelated test fixtures and conflicts with other tests' contexts. The gating predicates are
 * identical regardless of how the nested config is registered, and the full
 * {@code @IntegrationTest}/{@code @SecurityTest} suite remains the oracle for end-to-end wiring with the
 * real entry point.
 */
@DisplayName("UserConfiguration Cross-Cutting Toggle Tests")
class UserConfigurationToggleTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(
            UserConfiguration.AsyncConfiguration.class, UserConfiguration.RetryConfiguration.class,
            UserConfiguration.SchedulingConfiguration.class, UserConfiguration.MethodSecurityConfiguration.class);

    @Test
    @DisplayName("Defaults (no properties set): all nested cross-cutting configs are active")
    void defaultsActivateAllCrossCuttingConfigs() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(UserConfiguration.AsyncConfiguration.class);
            assertThat(ctx).hasSingleBean(UserConfiguration.RetryConfiguration.class);
            assertThat(ctx).hasSingleBean(UserConfiguration.SchedulingConfiguration.class);
            assertThat(ctx).hasSingleBean(UserConfiguration.MethodSecurityConfiguration.class);
        });
    }

    @Test
    @DisplayName("user.method-security.enabled=false: method security nested config does not activate")
    void methodSecurityToggleOff() {
        runner.withPropertyValues("user.method-security.enabled=false").run(ctx -> {
            assertThat(ctx).doesNotHaveBean(UserConfiguration.MethodSecurityConfiguration.class);
            // Other cross-cutting configs remain active and independent.
            assertThat(ctx).hasSingleBean(UserConfiguration.AsyncConfiguration.class);
            assertThat(ctx).hasSingleBean(UserConfiguration.RetryConfiguration.class);
            assertThat(ctx).hasSingleBean(UserConfiguration.SchedulingConfiguration.class);
        });
    }

    @Test
    @DisplayName("user.method-security.enabled=true (explicit): method security nested config activates")
    void methodSecurityToggleOnExplicit() {
        runner.withPropertyValues("user.method-security.enabled=true")
                .run(ctx -> assertThat(ctx).hasSingleBean(UserConfiguration.MethodSecurityConfiguration.class));
    }

    @Test
    @DisplayName("user.scheduling.enabled=false: scheduling nested config does not activate")
    void schedulingToggleOff() {
        runner.withPropertyValues("user.scheduling.enabled=false").run(ctx -> {
            assertThat(ctx).doesNotHaveBean(UserConfiguration.SchedulingConfiguration.class);
            assertThat(ctx).hasSingleBean(UserConfiguration.AsyncConfiguration.class);
        });
    }

    @Test
    @DisplayName("user.async.enabled=false: async nested config does not activate")
    void asyncToggleOff() {
        runner.withPropertyValues("user.async.enabled=false").run(ctx -> {
            assertThat(ctx).doesNotHaveBean(UserConfiguration.AsyncConfiguration.class);
            assertThat(ctx).hasSingleBean(UserConfiguration.RetryConfiguration.class);
        });
    }

    @Test
    @DisplayName("user.retry.enabled=false: retry nested config does not activate")
    void retryToggleOff() {
        runner.withPropertyValues("user.retry.enabled=false").run(ctx -> {
            assertThat(ctx).doesNotHaveBean(UserConfiguration.RetryConfiguration.class);
            // Other cross-cutting configs remain active and independent.
            assertThat(ctx).hasSingleBean(UserConfiguration.AsyncConfiguration.class);
        });
    }
}
