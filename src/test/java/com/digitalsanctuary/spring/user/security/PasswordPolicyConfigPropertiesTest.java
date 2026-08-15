package com.digitalsanctuary.spring.user.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

@DisplayName("PasswordPolicyConfigProperties binding")
class PasswordPolicyConfigPropertiesTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PasswordPolicyConfigProperties.class)
    static class TestConfig {
    }

    @Test
    void shouldApplyShippedDefaultsWhenUnset() {
        contextRunner.run(context -> {
            PasswordPolicyConfigProperties p = context.getBean(PasswordPolicyConfigProperties.class);
            assertThat(p.isEnabled()).isTrue();
            assertThat(p.getMinLength()).isEqualTo(8);
            assertThat(p.getMaxLength()).isEqualTo(128);
            assertThat(p.getHistoryCount()).isEqualTo(3);
            assertThat(p.getSimilarityThreshold()).isEqualTo(70);
        });
    }

    @Test
    void shouldBindKebabKeysWhenConfigured() {
        contextRunner.withPropertyValues("user.security.password.min-length=12",
                "user.security.password.require-special=false",
                "user.security.password.history-count=5").run(context -> {
            PasswordPolicyConfigProperties p = context.getBean(PasswordPolicyConfigProperties.class);
            assertThat(p.getMinLength()).isEqualTo(12);
            assertThat(p.isRequireSpecial()).isFalse();
            assertThat(p.getHistoryCount()).isEqualTo(5);
        });
    }

    // These startup-failure tests exercise Bean Validation on the bound properties, which is active because
    // hibernate-validator is on the test classpath. In a consuming app without a validator the constraints are
    // inert (documented on the class); with one, an impossible policy fails startup with the property named.
    @Test
    void shouldFailStartupWhenMinLengthExceedsMaxLength() {
        contextRunner.withPropertyValues("user.security.password.min-length=20",
                "user.security.password.max-length=10").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause()
                    .hasMessageContaining("minLength must be less than or equal to maxLength");
        });
    }

    @Test
    void shouldFailStartupWhenRequireSpecialWithEmptySpecialChars() {
        contextRunner.withPropertyValues("user.security.password.special-chars=").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause()
                    .hasMessageContaining("specialChars must not be empty");
        });
    }

    @Test
    void shouldFailStartupWhenSimilarityThresholdOutsideRange() {
        contextRunner.withPropertyValues("user.security.password.similarity-threshold=150").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("similarityThreshold");
        });
    }
}
