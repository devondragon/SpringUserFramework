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
 * ({@link SecurityFilterProperties#BASIC_AUTH_ORDER}) and backs off entirely (via {@link ConditionalOnMissingBean}) when a consuming application defines its own
 * {@link SecurityFilterChain}.
 * <p>
 * The chain is contributed by {@link WebSecurityFilterChainAutoConfiguration} (an auto-configuration), which delegates construction to
 * {@link WebSecurityConfig#buildSecurityFilterChain}. It lives on an auto-configuration class &mdash; rather than directly as a {@code @Bean} on the
 * component-scanned {@link WebSecurityConfig} &mdash; precisely because {@code @ConditionalOnMissingBean} is only reliable on auto-configuration
 * classes (which load after user-defined bean definitions).
 * </p>
 * <p>
 * The real bean method requires the full security context and many collaborators to invoke. Rather than boot that heavyweight context (which would
 * also risk polluting the shared JPA metamodel across parallel integration contexts), this test verifies the composition contract in two
 * complementary, isolated ways:
 * </p>
 * <ol>
 * <li>A reflection assertion that the auto-configuration's {@code securityFilterChain} method carries
 * {@code @ConditionalOnMissingBean(SecurityFilterChain.class)} and a low-precedence {@code @Order}.</li>
 * <li>An {@link ApplicationContextRunner} test against a lightweight stand-in auto-configuration that mirrors the exact same conditional/order
 * semantics, proving the back-off behaviour when a consumer supplies their own chain.</li>
 * </ol>
 */
@DisplayName("WebSecurityConfig SecurityFilterChain Composition Tests")
class WebSecurityConfigCompositionTest {

    @Nested
    @DisplayName("Annotation Contract on the auto-configuration bean method")
    class AnnotationContract {

        @Test
        @DisplayName("securityFilterChain is annotated @ConditionalOnMissingBean(SecurityFilterChain.class)")
        void securityFilterChainIsConditionalOnMissingBean() throws Exception {
            Method method = WebSecurityFilterChainAutoConfiguration.class.getMethod("securityFilterChain", HttpSecurity.class, SessionRegistry.class);
            ConditionalOnMissingBean conditional = method.getAnnotation(ConditionalOnMissingBean.class);
            assertThat(conditional).as("@ConditionalOnMissingBean must be present").isNotNull();
            assertThat(conditional.value()).as("must back off when any SecurityFilterChain is present").contains(SecurityFilterChain.class);
        }

        @Test
        @DisplayName("securityFilterChain is annotated with a low-precedence @Order so consumer chains win")
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
    @DisplayName("Back-off semantics via ApplicationContextRunner")
    class BackOffSemantics {

        // Register the library stand-in as an auto-configuration so it is processed AFTER user-defined beans,
        // which is required for @ConditionalOnMissingBean to evaluate correctly.
        private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(LibraryChainConfiguration.class));

        @Test
        @DisplayName("Library SecurityFilterChain is present by default")
        void libraryChainPresentByDefault() {
            contextRunner.run(context -> {
                assertThat(context).hasSingleBean(SecurityFilterChain.class);
                assertThat(context).hasBean("librarySecurityFilterChain");
            });
        }

        @Test
        @DisplayName("Library SecurityFilterChain backs off when a consumer defines their own")
        void libraryChainBacksOffWhenConsumerSuppliesChain() {
            contextRunner
                    .withUserConfiguration(ConsumerChainConfiguration.class)
                    .run(context -> {
                        assertThat(context).hasSingleBean(SecurityFilterChain.class);
                        assertThat(context).hasBean("consumerSecurityFilterChain");
                        assertThat(context).doesNotHaveBean("librarySecurityFilterChain");
                    });
        }
    }

    /**
     * Lightweight stand-in mirroring the auto-configuration's composition contract (same annotations as
     * {@link WebSecurityFilterChainAutoConfiguration#securityFilterChain}). Returns a Mockito mock so no servlet/security context is required.
     * <p>
     * Intentionally NOT annotated with {@code @Configuration}: that would make this nested class eligible for the integration tests' component scan
     * ({@code com.digitalsanctuary.spring.user}) and pollute those contexts with stray {@link SecurityFilterChain} beans. The
     * {@link ApplicationContextRunner} registers these classes explicitly via {@code withConfiguration}/{@code withUserConfiguration}, so they are
     * still processed as (lite) configuration sources without being scannable.
     * </p>
     */
    static class LibraryChainConfiguration {
        @Bean
        @Order(SecurityFilterProperties.BASIC_AUTH_ORDER)
        @ConditionalOnMissingBean(SecurityFilterChain.class)
        public SecurityFilterChain librarySecurityFilterChain() {
            return org.mockito.Mockito.mock(SecurityFilterChain.class);
        }
    }

    /**
     * A trivial consumer-supplied chain that should win and suppress the library's chain. Intentionally NOT annotated with {@code @Configuration} so it
     * is not component-scanned by the integration tests (see {@link LibraryChainConfiguration}).
     */
    static class ConsumerChainConfiguration {
        @Bean
        public SecurityFilterChain consumerSecurityFilterChain() {
            return org.mockito.Mockito.mock(SecurityFilterChain.class);
        }
    }
}
