package com.digitalsanctuary.spring.user.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

@DisplayName("HtmxAwareAuthenticationEntryPointConfiguration Tests")
class HtmxAwareAuthenticationEntryPointConfigurationTest {

    // Register as auto-configuration so it is processed after user-defined beans,
    // which is required for @ConditionalOnMissingBean to evaluate correctly.
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(HtmxAwareAuthenticationEntryPointConfiguration.class))
            .withPropertyValues(
                    "user.security.loginPageURI=/user/login.html"
            );

    @Nested
    @DisplayName("Non-OAuth2 Configuration")
    class NonOAuth2Configuration {

        @Test
        @DisplayName("Should register HtmxAwareAuthenticationEntryPoint wrapping LoginUrlAuthenticationEntryPoint when OAuth2 disabled")
        void shouldRegisterHtmxEntryPointWrappingLoginUrlWhenOAuth2Disabled() {
            contextRunner
                    .withPropertyValues("spring.security.oauth2.enabled=false")
                    .run(context -> {
                        assertThat(context).hasSingleBean(AuthenticationEntryPoint.class);
                        assertThat(context.getBean(AuthenticationEntryPoint.class))
                                .isInstanceOf(HtmxAwareAuthenticationEntryPoint.class);
                    });
        }

        @Test
        @DisplayName("Should register HtmxAwareAuthenticationEntryPoint when OAuth2 property absent")
        void shouldRegisterHtmxEntryPointWhenOAuth2PropertyAbsent() {
            contextRunner.run(context -> {
                assertThat(context).hasSingleBean(AuthenticationEntryPoint.class);
                assertThat(context.getBean(AuthenticationEntryPoint.class))
                        .isInstanceOf(HtmxAwareAuthenticationEntryPoint.class);
            });
        }
    }

    @Nested
    @DisplayName("OAuth2 Configuration")
    class OAuth2Configuration {

        @Test
        @DisplayName("Should register HtmxAwareAuthenticationEntryPoint when OAuth2 enabled")
        void shouldRegisterHtmxEntryPointWhenOAuth2Enabled() {
            contextRunner
                    .withPropertyValues("spring.security.oauth2.enabled=true")
                    .run(context -> {
                        assertThat(context).hasSingleBean(AuthenticationEntryPoint.class);
                        assertThat(context.getBean(AuthenticationEntryPoint.class))
                                .isInstanceOf(HtmxAwareAuthenticationEntryPoint.class);
                    });
        }
    }

    @Nested
    @DisplayName("Consumer Override via @ConditionalOnMissingBean")
    class ConsumerOverride {

        @Test
        @DisplayName("Should not register library bean when consumer provides an AuthenticationEntryPoint")
        void shouldNotRegisterLibraryBeanWhenConsumerProvidesEntryPoint() {
            contextRunner
                    .withUserConfiguration(ConsumerEntryPointConfiguration.class)
                    .run(context -> {
                        assertThat(context).hasSingleBean(AuthenticationEntryPoint.class);
                        assertThat(context.getBean(AuthenticationEntryPoint.class))
                                .isInstanceOf(LoginUrlAuthenticationEntryPoint.class);
                    });
        }
    }

    @Configuration
    static class ConsumerEntryPointConfiguration {
        @Bean
        public AuthenticationEntryPoint consumerEntryPoint() {
            return new LoginUrlAuthenticationEntryPoint("/custom/login");
        }
    }
}
