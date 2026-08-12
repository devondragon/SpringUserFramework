package com.digitalsanctuary.spring.user.captcha;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

@DisplayName("CaptchaConfigProperties binding")
class CaptchaConfigPropertiesTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(PropertiesTestConfiguration.class);

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CaptchaConfigProperties.class)
    static class PropertiesTestConfiguration {
    }

    @Test
    void shouldDefaultToDisabledTurnstileWithEmailActionsProtected() {
        contextRunner.run(context -> {
            CaptchaConfigProperties properties = context.getBean(CaptchaConfigProperties.class);
            assertThat(properties.isEnabled()).isFalse();
            assertThat(properties.getProvider()).isEqualTo("turnstile");
            assertThat(properties.isAllowUnusableProvider()).isFalse();
            assertThat(properties.getProtect().isRegistration()).isTrue();
            assertThat(properties.getProtect().isPasswordlessRegistration()).isTrue();
            assertThat(properties.getProtect().isResetPassword()).isTrue();
            assertThat(properties.getProtect().isResendRegistrationToken()).isTrue();
        });
    }

    @Test
    void shouldProtectEveryCaptchaActionByDefault() {
        // Guards against a CaptchaAction constant being added without a matching Protect toggle,
        // which would leave the new action unprotected by default rather than protected.
        contextRunner.run(context -> {
            CaptchaConfigProperties.Protect protect =
                    context.getBean(CaptchaConfigProperties.class).getProtect();
            for (CaptchaAction action : CaptchaAction.values()) {
                assertThat(protect.isProtected(action)).as("default protection for %s", action).isTrue();
            }
        });
    }

    @Test
    void shouldBindKebabCasePropertiesWhenConfigured() {
        contextRunner
                .withPropertyValues("user.security.captcha.enabled=true", "user.security.captcha.provider=turnstile",
                        "user.security.captcha.allow-unusable-provider=true",
                        "user.security.captcha.protect.registration=false",
                        "user.security.captcha.protect.passwordless-registration=false",
                        "user.security.captcha.protect.reset-password=false",
                        "user.security.captcha.protect.resend-registration-token=false")
                .run(context -> {
                    CaptchaConfigProperties properties = context.getBean(CaptchaConfigProperties.class);
                    assertThat(properties.isEnabled()).isTrue();
                    assertThat(properties.isAllowUnusableProvider()).isTrue();
                    assertThat(properties.getProtect().isRegistration()).isFalse();
                    assertThat(properties.getProtect().isPasswordlessRegistration()).isFalse();
                    assertThat(properties.getProtect().isResetPassword()).isFalse();
                    assertThat(properties.getProtect().isResendRegistrationToken()).isFalse();
                });
    }
}
