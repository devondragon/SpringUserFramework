package com.digitalsanctuary.spring.user.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

@DisplayName("RememberMeConfigProperties binding")
class RememberMeConfigPropertiesTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(RememberMeConfigProperties.class)
    static class TestConfig {
    }

    @Test
    void shouldApplyDefaultsWhenUnset() {
        contextRunner.run(context -> {
            RememberMeConfigProperties p = context.getBean(RememberMeConfigProperties.class);
            assertThat(p.isEnabled()).isFalse();
            assertThat(p.getKey()).isNull();
            assertThat(p.getTokenValiditySeconds()).isEqualTo(1209600);
            assertThat(p.getRememberMeParameter()).isEqualTo("remember-me");
            assertThat(p.getRememberMeCookieName()).isEqualTo("remember-me");
            assertThat(p.getUseSecureCookie()).isNull();
            assertThat(p.isUsePersistentTokens()).isFalse();
        });
    }

    @Test
    void shouldBindLegacyCamelCaseKeysWhenRelaxedBindingApplies() {
        contextRunner.withPropertyValues("user.security.rememberMe.enabled=true",
                "user.security.rememberMe.tokenValiditySeconds=60",
                "user.security.rememberMe.useSecureCookie=true").run(context -> {
            RememberMeConfigProperties p = context.getBean(RememberMeConfigProperties.class);
            assertThat(p.isEnabled()).isTrue();
            assertThat(p.getTokenValiditySeconds()).isEqualTo(60);
            assertThat(p.getUseSecureCookie()).isTrue();
        });
    }
}
