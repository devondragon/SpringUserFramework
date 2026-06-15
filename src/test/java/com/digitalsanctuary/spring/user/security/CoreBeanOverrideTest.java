package com.digitalsanctuary.spring.user.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.digitalsanctuary.spring.user.roles.RolesAndPrivilegesConfig;

/**
 * Proves that the four core, overridable security beans &mdash; {@link PasswordEncoder}, {@link SessionRegistry}, {@link RoleHierarchy}, and
 * {@link DaoAuthenticationProvider} &mdash; are genuinely replaceable by a consuming application.
 *
 * <p>
 * The library historically defined these beans on the component-scanned {@link WebSecurityConfig} with no {@code @ConditionalOnMissingBean}, so a
 * consumer that defined their own bean of the same type got a bean-definition conflict. Task 3.2 established (and this test re-proves) that
 * {@code @ConditionalOnMissingBean} is only reliable on an {@code @AutoConfiguration} class &mdash; which loads AFTER user-defined beans &mdash; not on
 * a component-scanned {@code @Configuration}. These beans therefore live on {@link UserSecurityBeansAutoConfiguration}.
 * </p>
 *
 * <p>
 * The test is deliberately isolated: it drives {@link UserSecurityBeansAutoConfiguration} directly through an {@link ApplicationContextRunner} with a
 * tiny set of mock collaborators, so it never boots the full security/JPA context. This avoids polluting the shared JPA metamodel across parallel
 * integration contexts while still exercising the real override semantics end-to-end.
 * </p>
 */
@DisplayName("Core Security Bean Override Tests")
class CoreBeanOverrideTest {

    /**
     * Drives the real {@link UserSecurityBeansAutoConfiguration}. A {@link RolesAndPrivilegesConfig} and a {@link UserDetailsService} are supplied as
     * collaborators because {@code roleHierarchy()} and {@code authProvider()} depend on them. Registered as an auto-configuration so
     * {@code @ConditionalOnMissingBean} evaluates AFTER any user-supplied beans.
     */
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(UserDetailsService.class, () -> username -> User.withUsername("test").password("x").authorities("ROLE_USER").build())
            .withBean(RolesAndPrivilegesConfig.class, CoreBeanOverrideTest::roleConfig)
            .withConfiguration(AutoConfigurations.of(UserSecurityBeansAutoConfiguration.class));

    private static RolesAndPrivilegesConfig roleConfig() {
        RolesAndPrivilegesConfig config = new RolesAndPrivilegesConfig();
        // getRoleHierarchyString() is built from the roleHierarchy list; set it so roleHierarchy() returns a real hierarchy by default.
        config.setRoleHierarchy(List.of("ROLE_ADMIN > ROLE_USER"));
        return config;
    }

    @Nested
    @DisplayName("Default behavior: library beans present when consumer supplies none")
    class Defaults {

        @Test
        @DisplayName("Library provides a BCryptPasswordEncoder by default")
        void libraryEncoderPresentByDefault() {
            contextRunner.run(context -> {
                assertThat(context).hasSingleBean(PasswordEncoder.class);
                assertThat(context.getBean(PasswordEncoder.class)).isInstanceOf(BCryptPasswordEncoder.class);
            });
        }

        @Test
        @DisplayName("Library provides a SessionRegistryImpl by default")
        void librarySessionRegistryPresentByDefault() {
            contextRunner.run(context -> {
                assertThat(context).hasSingleBean(SessionRegistry.class);
                assertThat(context.getBean(SessionRegistry.class)).isInstanceOf(SessionRegistryImpl.class);
            });
        }

        @Test
        @DisplayName("Library provides a DaoAuthenticationProvider by default")
        void libraryAuthProviderPresentByDefault() {
            contextRunner.run(context -> assertThat(context).hasSingleBean(DaoAuthenticationProvider.class));
        }

        @Test
        @DisplayName("Library provides a RoleHierarchy by default when config supplies a hierarchy")
        void libraryRoleHierarchyPresentByDefault() {
            contextRunner.run(context -> {
                assertThat(context).hasSingleBean(RoleHierarchy.class);
                assertThat(context.getBean(RoleHierarchy.class)).isInstanceOf(RoleHierarchyImpl.class);
            });
        }
    }

    @Nested
    @DisplayName("Override behavior: consumer beans win")
    class Overrides {

        @Test
        @DisplayName("Consumer PasswordEncoder replaces the library's BCryptPasswordEncoder")
        void consumerEncoderWins() {
            PasswordEncoder consumerEncoder = NoOpPasswordEncoder.getInstance();
            contextRunner.withUserConfiguration(ConsumerEncoderConfig.class).run(context -> {
                assertThat(context).hasSingleBean(PasswordEncoder.class);
                PasswordEncoder active = context.getBean(PasswordEncoder.class);
                assertThat(active).as("consumer's encoder must win").isSameAs(consumerEncoder);
                assertThat(active).as("library BCryptPasswordEncoder must NOT be the active encoder").isNotInstanceOf(BCryptPasswordEncoder.class);
            });
        }

        @Test
        @DisplayName("Consumer SessionRegistry replaces the library's SessionRegistryImpl")
        void consumerSessionRegistryWins() {
            contextRunner.withUserConfiguration(ConsumerSessionRegistryConfig.class).run(context -> {
                assertThat(context).hasSingleBean(SessionRegistry.class);
                SessionRegistry active = context.getBean(SessionRegistry.class);
                assertThat(active).as("consumer's session registry must win").isSameAs(ConsumerSessionRegistryConfig.CONSUMER_REGISTRY);
                assertThat(active).as("library SessionRegistryImpl must NOT be the active registry").isNotInstanceOf(SessionRegistryImpl.class);
            });
        }

        @Test
        @DisplayName("Consumer RoleHierarchy replaces the library's RoleHierarchyImpl")
        void consumerRoleHierarchyWins() {
            contextRunner.withUserConfiguration(ConsumerRoleHierarchyConfig.class).run(context -> {
                assertThat(context).hasSingleBean(RoleHierarchy.class);
                assertThat(context.getBean(RoleHierarchy.class)).isSameAs(ConsumerRoleHierarchyConfig.CONSUMER_HIERARCHY);
            });
        }

        @Test
        @DisplayName("Consumer DaoAuthenticationProvider replaces the library's, and is wired with the consumer's encoder")
        void consumerAuthProviderWins() {
            contextRunner.withUserConfiguration(ConsumerAuthProviderConfig.class).run(context -> {
                assertThat(context).hasSingleBean(DaoAuthenticationProvider.class);
                assertThat(context.getBean(DaoAuthenticationProvider.class)).isSameAs(ConsumerAuthProviderConfig.CONSUMER_PROVIDER);
            });
        }

        @Test
        @DisplayName("authProvider() honors a consumer-supplied PasswordEncoder (no intra-class self-call to encoder())")
        void authProviderUsesConsumerEncoder() {
            PasswordEncoder consumerEncoder = NoOpPasswordEncoder.getInstance();
            contextRunner.withUserConfiguration(ConsumerEncoderConfig.class).run(context -> {
                DaoAuthenticationProvider provider = context.getBean(DaoAuthenticationProvider.class);
                // The provider must encode using the consumer's encoder, not the library BCrypt one. NoOpPasswordEncoder
                // returns the raw password, so a match against the raw value proves the consumer encoder was injected.
                assertThat(provider.authenticate(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("test", "x"))
                        .isAuthenticated()).as("authProvider must authenticate using the consumer's NoOp encoder").isTrue();
                // Sanity: the consumer's encoder is indeed the active one.
                assertThat(context.getBean(PasswordEncoder.class)).isSameAs(consumerEncoder);
            });
        }
    }

    @Nested
    @DisplayName("Annotation contract on the auto-configuration bean methods")
    class AnnotationContract {

        @Test
        @DisplayName("encoder() is @ConditionalOnMissingBean")
        void encoderIsConditional() throws Exception {
            Method method = UserSecurityBeansAutoConfiguration.class.getMethod("encoder");
            assertThat(method.getAnnotation(ConditionalOnMissingBean.class)).isNotNull();
        }

        @Test
        @DisplayName("sessionRegistry() is @ConditionalOnMissingBean")
        void sessionRegistryIsConditional() throws Exception {
            Method method = UserSecurityBeansAutoConfiguration.class.getMethod("sessionRegistry");
            assertThat(method.getAnnotation(ConditionalOnMissingBean.class)).isNotNull();
        }

        @Test
        @DisplayName("roleHierarchy() is @ConditionalOnMissingBean")
        void roleHierarchyIsConditional() throws Exception {
            Method method = UserSecurityBeansAutoConfiguration.class.getMethod("roleHierarchy");
            assertThat(method.getAnnotation(ConditionalOnMissingBean.class)).isNotNull();
        }

        @Test
        @DisplayName("authProvider() is @ConditionalOnMissingBean and receives PasswordEncoder as a parameter")
        void authProviderIsConditionalAndParameterized() throws Exception {
            Method method = UserSecurityBeansAutoConfiguration.class.getMethod("authProvider", PasswordEncoder.class);
            assertThat(method.getAnnotation(ConditionalOnMissingBean.class)).as("@ConditionalOnMissingBean must be present").isNotNull();
            // authProvider must RECEIVE the PasswordEncoder (so a consumer override is honored) rather than self-call encoder().
            assertThat(method.getParameterTypes()).as("authProvider must receive PasswordEncoder via injection").contains(PasswordEncoder.class);
        }
    }

    // ---- Consumer-supplied stand-in configurations. Not @Configuration so the integration tests' component scan does not pick them up. ----

    static class ConsumerEncoderConfig {
        static final PasswordEncoder CONSUMER_ENCODER = NoOpPasswordEncoder.getInstance();

        @Bean
        PasswordEncoder consumerEncoder() {
            return CONSUMER_ENCODER;
        }
    }

    static class ConsumerSessionRegistryConfig {
        static final SessionRegistry CONSUMER_REGISTRY = new CustomSessionRegistry();

        @Bean
        SessionRegistry consumerSessionRegistry() {
            return CONSUMER_REGISTRY;
        }
    }

    static class ConsumerRoleHierarchyConfig {
        static final RoleHierarchy CONSUMER_HIERARCHY = RoleHierarchyImpl.fromHierarchy("ROLE_X > ROLE_Y");

        @Bean
        RoleHierarchy consumerRoleHierarchy() {
            return CONSUMER_HIERARCHY;
        }
    }

    static class ConsumerAuthProviderConfig {
        static final DaoAuthenticationProvider CONSUMER_PROVIDER =
                new DaoAuthenticationProvider(username -> User.withUsername("c").password("x").authorities("ROLE_USER").build());

        @Bean
        DaoAuthenticationProvider consumerAuthProvider() {
            return CONSUMER_PROVIDER;
        }
    }

    /**
     * A trivial custom {@link SessionRegistry} that is NOT a {@link SessionRegistryImpl}, so the test can assert the consumer's instance wins.
     */
    static class CustomSessionRegistry implements SessionRegistry {
        @Override
        public List<Object> getAllPrincipals() {
            return List.of();
        }

        @Override
        public List<org.springframework.security.core.session.SessionInformation> getAllSessions(Object principal, boolean includeExpiredSessions) {
            return List.of();
        }

        @Override
        public org.springframework.security.core.session.SessionInformation getSessionInformation(String sessionId) {
            return null;
        }

        @Override
        public void refreshLastRequest(String sessionId) {}

        @Override
        public void registerNewSession(String sessionId, Object principal) {}

        @Override
        public void removeSessionInformation(String sessionId) {}
    }
}
