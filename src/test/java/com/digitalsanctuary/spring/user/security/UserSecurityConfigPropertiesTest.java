package com.digitalsanctuary.spring.user.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

@DisplayName("UserSecurityConfigProperties binding")
class UserSecurityConfigPropertiesTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(UserSecurityConfigProperties.class)
    static class TestConfig {
    }

    @Test
    void shouldApplyShippedDefaultsWhenUnset() {
        contextRunner.run(context -> {
            UserSecurityConfigProperties p = context.getBean(UserSecurityConfigProperties.class);
            assertThat(p.getLoginPageUri()).isEqualTo("/user/login.html");
            assertThat(p.getRegistrationUri()).isEqualTo("/user/register.html");
            assertThat(p.getDefaultAction()).isEqualTo("deny");
            assertThat(p.getBcryptStrength()).isEqualTo(12);
            assertThat(p.getAppUrl()).isEqualTo("");
            assertThat(p.getTokenHashSecret()).isNull();
            assertThat(p.getProtectedUris()).containsExactly("/protected.html");
            assertThat(p.getUnprotectedUris()).containsExactly("/", "/index.html", "/favicon.ico", "/css/*",
                    "/js/*", "/img/*", "/user/registration", "/user/resendRegistrationToken",
                    "/user/resetPassword", "/user/registrationConfirm", "/user/changePassword",
                    "/user/savePassword", "/oauth2/authorization/*", "/login", "/error");
        });
    }

    @Test
    void shouldBindLegacyCamelCaseUriKeysWhenSetWithOldSpelling() {
        contextRunner.withPropertyValues("user.security.loginPageURI=/custom/login").run(context -> {
            assertThat(context.getBean(UserSecurityConfigProperties.class).getLoginPageUri())
                    .isEqualTo("/custom/login");
        });
    }

    @Test
    void shouldDropBlankSegmentsWhenBindingUriLists() {
        contextRunner.withPropertyValues("user.security.unprotectedURIs=/a,,/b,").run(context -> {
            assertThat(context.getBean(UserSecurityConfigProperties.class).getUnprotectedUris())
                    .containsExactly("/a", "/b");
        });
    }

    @Test
    void shouldReturnEmptyListWhenUriListPropertyBlank() {
        contextRunner.withPropertyValues("user.security.disableCSRFURIs=").run(context -> {
            assertThat(context.getBean(UserSecurityConfigProperties.class).getDisableCsrfUris()).isEmpty();
        });
    }
}
