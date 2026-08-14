package com.digitalsanctuary.spring.user.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@DisplayName("Mapping-placeholder keys stay in sync with the bound bean")
class UriPlaceholderParityTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(UserSecurityConfigProperties.class)
    static class TestConfig {
    }

    @Test
    void shouldEqualBeanGetterWhenEnvironmentResolvesCamelCaseKey() {
        // Keys used as @GetMapping/@RequestMapping/@ConditionalOnProperty placeholders elsewhere in the framework.
        Map<String, java.util.function.Function<UserSecurityConfigProperties, String>> mappingKeys = Map.of(
                "user.security.loginPageURI", UserSecurityConfigProperties::getLoginPageUri,
                "user.security.registrationURI", UserSecurityConfigProperties::getRegistrationUri,
                "user.security.changePasswordURI", UserSecurityConfigProperties::getChangePasswordUri,
                "user.security.forgotPasswordChangeURI", UserSecurityConfigProperties::getForgotPasswordChangeUri,
                "user.security.registrationConfirmURI", UserSecurityConfigProperties::getRegistrationConfirmUri);

        new ApplicationContextRunner().withUserConfiguration(TestConfig.class)
                .withPropertyValues("user.security.loginPageURI=/user/login.html",
                        "user.security.registrationURI=/user/register.html",
                        "user.security.changePasswordURI=/user/changePassword",
                        "user.security.forgotPasswordChangeURI=/user/forgot-password-change.html",
                        "user.security.registrationConfirmURI=/user/registrationConfirm")
                .run(context -> {
                    Environment env = context.getEnvironment();
                    UserSecurityConfigProperties bean = context.getBean(UserSecurityConfigProperties.class);
                    mappingKeys.forEach((key, getter) -> assertThat(getter.apply(bean))
                            .as("bean value for %s must equal the placeholder-resolved Environment value", key)
                            .isEqualTo(env.getProperty(key)));
                });
    }
}
