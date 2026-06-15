package com.digitalsanctuary.spring.user.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.data.domain.AuditorAware;

/**
 * Tests for the conditional gating of JPA auditing (H5).
 * <p>
 * The library's {@link JpaAuditingConfig} is gated by {@code user.jpa.auditing.enabled} (default {@code true}). When the
 * property is {@code false}, the whole configuration — including {@code @EnableJpaAuditing} and the
 * {@code auditorProvider} {@link AuditorAware} bean — is skipped, so a consuming application can run its own JPA
 * auditing without the library hijacking it.
 * </p>
 * <p>
 * <strong>Why the "enabled" cases use a stand-in:</strong> {@link JpaAuditingConfig} carries {@code @EnableJpaAuditing},
 * which eagerly initializes a {@code JpaMetamodelMappingContext}. Bootstrapping that machinery inside a unit-slice
 * context (and supplying a mock {@code EntityManagerFactory} to satisfy it) pollutes the JPA metamodel shared by this
 * module's parallel integration-test contexts, causing unrelated "domain class can not be found in the given Metamodel"
 * failures. So the "enabled" assertions use {@link GatedConfiguration}, a stand-in that mirrors
 * {@link JpaAuditingConfig}'s exact class-level {@code @ConditionalOnProperty} but omits {@code @EnableJpaAuditing}.
 * The "disabled" case is asserted against the real {@link JpaAuditingConfig}, which is safe because the gate skips the
 * auditing machinery entirely. The full {@code @EnableJpaAuditing} wiring (and that auditing works by default) is
 * exercised by the integration tests running against the real {@code TestApplication} context.
 * </p>
 *
 * @see JpaAuditingConfig
 */
@DisplayName("JpaAuditingConfig Conditional Gating Tests")
class JpaAuditingConfigTest {

    /** Runner over the REAL config — used only for the disabled case, where no auditing machinery initializes. */
    private final ApplicationContextRunner realConfigRunner =
            new ApplicationContextRunner().withUserConfiguration(JpaAuditingConfig.class);

    /** Runner over the stand-in — used for the enabled cases to avoid bootstrapping the JPA metamodel. */
    private final ApplicationContextRunner gatedRunner =
            new ApplicationContextRunner().withUserConfiguration(GatedConfiguration.class);

    /**
     * Mirrors {@link JpaAuditingConfig}'s class-level gate exactly, exposing a marker bean that reflects whether the
     * gated configuration is active. Deliberately omits {@code @EnableJpaAuditing} so no JPA metamodel is initialized.
     */
    // @TestConfiguration (not @Configuration) so the library's @ComponentScan TypeExcludeFilter keeps this
    // stand-in out of integration contexts; it is still applied where used via withUserConfiguration(...).
    @TestConfiguration
    @ConditionalOnProperty(name = "user.jpa.auditing.enabled", havingValue = "true", matchIfMissing = true)
    static class GatedConfiguration {
        @Bean
        String auditingGateMarker() {
            return "active";
        }
    }

    @Test
    @DisplayName("Should NOT register auditorProvider / AuditorAware when user.jpa.auditing.enabled=false")
    void shouldNotRegisterAuditorProviderWhenDisabled() {
        realConfigRunner.withPropertyValues("user.jpa.auditing.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.containsBean("auditorProvider")).isFalse();
            assertThat(context).doesNotHaveBean(AuditorAware.class);
        });
    }

    @Test
    @DisplayName("Should be ENABLED by default when the property is absent (backward compatible)")
    void shouldEnableByDefaultWhenPropertyAbsent() {
        gatedRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.containsBean("auditingGateMarker")).isTrue();
        });
    }

    @Test
    @DisplayName("Should be ENABLED when user.jpa.auditing.enabled=true")
    void shouldEnableWhenPropertyTrue() {
        gatedRunner.withPropertyValues("user.jpa.auditing.enabled=true").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.containsBean("auditingGateMarker")).isTrue();
        });
    }

    @Test
    @DisplayName("Should carry the exact @ConditionalOnProperty gate on the real JpaAuditingConfig class")
    void shouldCarryExactConditionalOnPropertyGateOnRealConfig() {
        // Pure reflection on the class annotation — no Spring context boot, so no @EnableJpaAuditing
        // metamodel initialization and zero metamodel-pollution risk. Fails fast if the gate is removed or weakened.
        var attributes =
                AnnotatedElementUtils.findMergedAnnotationAttributes(JpaAuditingConfig.class, ConditionalOnProperty.class, false, false);

        assertThat(attributes).as("@ConditionalOnProperty must be present on JpaAuditingConfig").isNotNull();
        assertThat(attributes.getStringArray("name")).containsExactly("user.jpa.auditing.enabled");
        assertThat(attributes.getString("havingValue")).isEqualTo("true");
        assertThat(attributes.getBoolean("matchIfMissing")).isTrue();
    }
}
