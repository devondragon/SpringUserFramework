package com.digitalsanctuary.spring.user.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Tests that the library's {@link SecurityFilterChain} bean is composable: it is contributed at a low precedence
 * ({@link SecurityFilterProperties#BASIC_AUTH_ORDER}) as the catch-all chain, and its back-off is keyed on the bean
 * <em>name</em> {@code securityFilterChain} (via {@link ConditionalOnMissingBean}).
 * <p>
 * This is deliberately <strong>name-based, not type-based</strong>. A type-based
 * {@code @ConditionalOnMissingBean(SecurityFilterChain.class)} would suppress the entire library chain the moment a
 * consumer defined <em>any</em> {@link SecurityFilterChain} &mdash; even a narrow, single-purpose one (e.g. a test-API or
 * actuator chain). That breaks the standard Spring Security multi-chain {@code @Order} layering pattern and silently
 * leaves the library's URIs unprotected. Keying on the bean name lets additional, differently-named consumer chains
 * coexist with the library chain, while still letting a consumer fully replace it by naming their bean
 * {@code securityFilterChain}.
 * </p>
 * <p>
 * The real bean method requires the full security context and many collaborators to invoke. Rather than boot that
 * heavyweight context (which would also risk polluting the shared JPA metamodel across parallel integration contexts),
 * this test verifies the composition contract in two complementary, isolated ways: a reflection assertion on the real
 * annotation, and an {@link ApplicationContextRunner} test against a lightweight stand-in that mirrors the exact same
 * name-based conditional/order semantics.
 * </p>
 */
@DisplayName("WebSecurityConfig SecurityFilterChain Composition Tests")
class WebSecurityConfigCompositionTest {

    @Nested
    @DisplayName("Annotation Contract on the auto-configuration bean method")
    class AnnotationContract {

        @Test
        @DisplayName("securityFilterChain backs off by bean NAME (not by type), so additional consumer chains coexist")
        void securityFilterChainIsConditionalOnMissingBeanByName() throws Exception {
            Method method = WebSecurityFilterChainAutoConfiguration.class.getMethod("securityFilterChain", HttpSecurity.class, SessionRegistry.class);
            ConditionalOnMissingBean conditional = method.getAnnotation(ConditionalOnMissingBean.class);
            assertThat(conditional).as("@ConditionalOnMissingBean must be present").isNotNull();
            assertThat(conditional.name())
                    .as("must back off only when a bean NAMED securityFilterChain is present (an explicit full replacement), "
                            + "so narrower consumer chains coexist instead of suppressing the library chain")
                    .contains("securityFilterChain");
            assertThat(conditional.value())
                    .as("must NOT be type-based: a type match would suppress the library chain whenever ANY SecurityFilterChain exists")
                    .isEmpty();
        }

        @Test
        @DisplayName("securityFilterChain is annotated with a low-precedence @Order so it is the catch-all and consumer chains win their matched paths")
        void securityFilterChainIsOrderedAtLowPrecedence() throws Exception {
            Method method = WebSecurityFilterChainAutoConfiguration.class.getMethod("securityFilterChain", HttpSecurity.class, SessionRegistry.class);
            Order order = method.getAnnotation(Order.class);
            assertThat(order).as("@Order must be present").isNotNull();
            // Matches Spring Boot's own default servlet security chain order (SecurityFilterProperties.BASIC_AUTH_ORDER,
            // relocated from SecurityProperties.BASIC_AUTH_ORDER in Spring Boot 4.0).
            assertThat(order.value()).as("library chain must be low precedence so consumer chains win")
                    .isEqualTo(SecurityFilterProperties.BASIC_AUTH_ORDER);
            assertThat(WebSecurityFilterChainAutoConfiguration.SECURITY_FILTER_CHAIN_ORDER).isEqualTo(SecurityFilterProperties.BASIC_AUTH_ORDER);
        }
    }

    @Nested
    @DisplayName("Composition semantics via ApplicationContextRunner")
    class CompositionSemantics {

        // Register the library stand-in as an auto-configuration so it is processed AFTER user-defined beans,
        // which is required for @ConditionalOnMissingBean to evaluate correctly.
        private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(LibraryChainConfiguration.class));

        @Test
        @DisplayName("Library SecurityFilterChain is present by default")
        void libraryChainPresentByDefault() {
            contextRunner.run(context -> {
                assertThat(context).hasSingleBean(SecurityFilterChain.class);
                assertThat(context).hasBean("securityFilterChain");
            });
        }

        @Test
        @DisplayName("Library chain COEXISTS with an additional, differently-named consumer chain (the standard multi-chain pattern)")
        void libraryChainCoexistsWithAdditionalConsumerChain() {
            contextRunner
                    .withUserConfiguration(AdditionalConsumerChainConfiguration.class)
                    .run(context -> {
                        // Both chains are present: a narrower, differently-named consumer chain must NOT suppress the
                        // library's catch-all chain. This is the regression guard for the test-API / actuator-chain case.
                        assertThat(context.getBeansOfType(SecurityFilterChain.class)).hasSize(2);
                        assertThat(context).hasBean("securityFilterChain");
                        assertThat(context).hasBean("apiSecurityFilterChain");
                    });
        }

        @Test
        @DisplayName("Library chain backs off ONLY when the consumer defines a bean NAMED securityFilterChain (explicit full replacement)")
        void libraryChainBacksOffOnNamedReplacement() {
            contextRunner
                    .withUserConfiguration(NamedReplacementChainConfiguration.class)
                    .run(context -> {
                        assertThat(context).hasSingleBean(SecurityFilterChain.class);
                        // The consumer's bean (named securityFilterChain) wins; the library's stand-in backs off.
                        assertThat(context).hasBean("securityFilterChain");
                    });
        }
    }

    /**
     * Lightweight stand-in mirroring the auto-configuration's composition contract (same name-based conditional and
     * order as {@link WebSecurityFilterChainAutoConfiguration#securityFilterChain}). The bean is named
     * {@code securityFilterChain} to match the real method's bean name. Returns a Mockito mock so no servlet/security
     * context is required.
     * <p>
     * Intentionally NOT annotated with {@code @Configuration}: that would make this nested class eligible for the
     * integration tests' component scan ({@code com.digitalsanctuary.spring.user}) and pollute those contexts with stray
     * {@link SecurityFilterChain} beans. The {@link ApplicationContextRunner} registers these classes explicitly via
     * {@code withConfiguration}/{@code withUserConfiguration}, so they are still processed as (lite) configuration
     * sources without being scannable.
     * </p>
     */
    static class LibraryChainConfiguration {
        @Bean
        @Order(SecurityFilterProperties.BASIC_AUTH_ORDER)
        @ConditionalOnMissingBean(name = "securityFilterChain")
        public SecurityFilterChain securityFilterChain() {
            return org.mockito.Mockito.mock(SecurityFilterChain.class);
        }
    }

    /**
     * An ADDITIONAL, narrower consumer chain with a different bean name and higher precedence (lower {@code @Order}),
     * exactly like a test-API or actuator chain. It must coexist with the library's catch-all chain. Intentionally NOT
     * annotated with {@code @Configuration} (see {@link LibraryChainConfiguration}).
     */
    static class AdditionalConsumerChainConfiguration {
        @Bean
        @Order(1)
        public SecurityFilterChain apiSecurityFilterChain() {
            return org.mockito.Mockito.mock(SecurityFilterChain.class);
        }
    }

    /**
     * A FULL-replacement consumer chain named exactly {@code securityFilterChain}; this is the explicit opt-in that
     * suppresses the library's chain. Intentionally NOT annotated with {@code @Configuration} (see
     * {@link LibraryChainConfiguration}).
     */
    static class NamedReplacementChainConfiguration {
        @Bean
        public SecurityFilterChain securityFilterChain() {
            return org.mockito.Mockito.mock(SecurityFilterChain.class);
        }
    }
}
