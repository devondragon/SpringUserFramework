package com.digitalsanctuary.spring.user.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;

import com.digitalsanctuary.spring.user.controller.UserActionController;
import com.digitalsanctuary.spring.user.controller.UserPageController;

@DisplayName("Mapping-placeholder keys stay in sync with the bound bean")
class UriPlaceholderParityTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(UserSecurityConfigProperties.class)
    static class TestConfig {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(UserSecurityConfigProperties.class)
    static class TestConfigWithValidator {
        @org.springframework.context.annotation.Bean
        UriPlaceholderParityValidator uriPlaceholderParityValidator(Environment environment,
                UserSecurityConfigProperties userSecurityConfig) {
            return new UriPlaceholderParityValidator(environment, userSecurityConfig);
        }
    }

    /**
     * Extracts every {@code ${user.security.<key>:<default>}} placeholder from the {@code @GetMapping}
     * annotations on the given controller class, mapped key -> inline default.
     */
    private static Map<String, String> mappingPlaceholders(Class<?> controller) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        for (Method method : controller.getDeclaredMethods()) {
            GetMapping mapping = method.getAnnotation(GetMapping.class);
            if (mapping == null) {
                continue;
            }
            for (String value : mapping.value()) {
                if (value.startsWith("${user.security.") && value.endsWith("}")) {
                    String inner = value.substring(2, value.length() - 1);
                    int colon = inner.indexOf(':');
                    assertThat(colon).as("placeholder %s must carry an inline default", value).isPositive();
                    placeholders.put(inner.substring(0, colon), inner.substring(colon + 1));
                }
            }
        }
        return placeholders;
    }

    @Test
    void shouldCoverEveryControllerPlaceholderWhenValidatorMapIsChecked() {
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.putAll(mappingPlaceholders(UserPageController.class));
        placeholders.putAll(mappingPlaceholders(UserActionController.class));

        // Every @GetMapping placeholder key must be covered by the startup validator, so a new mapped URI
        // property cannot be added without extending the parity guard.
        assertThat(UriPlaceholderParityValidator.MAPPING_PLACEHOLDER_KEYS.keySet())
                .containsExactlyInAnyOrderElementsOf(placeholders.keySet());

        // And every inline placeholder default must equal the field initializer, since both the placeholder
        // fallback and the validator's comparison fallback rely on that equivalence.
        UserSecurityConfigProperties defaults = new UserSecurityConfigProperties();
        placeholders.forEach((key, inlineDefault) -> assertThat(
                UriPlaceholderParityValidator.MAPPING_PLACEHOLDER_KEYS.get(key).apply(defaults))
                        .as("inline default of placeholder %s must equal the field initializer", key)
                        .isEqualTo(inlineDefault));
    }

    @Test
    void shouldEqualBeanGetterWhenEnvironmentResolvesCamelCaseKey() {
        String[] properties = UriPlaceholderParityValidator.MAPPING_PLACEHOLDER_KEYS.keySet().stream()
                .map(key -> key + "=/custom" + key.substring(key.lastIndexOf('.') + 1))
                .toArray(String[]::new);

        new ApplicationContextRunner().withUserConfiguration(TestConfig.class).withPropertyValues(properties)
                .run(context -> {
                    Environment env = context.getEnvironment();
                    UserSecurityConfigProperties bean = context.getBean(UserSecurityConfigProperties.class);
                    UriPlaceholderParityValidator.MAPPING_PLACEHOLDER_KEYS
                            .forEach((key, getter) -> assertThat(getter.apply(bean))
                                    .as("bean value for %s must equal the placeholder-resolved Environment value", key)
                                    .isEqualTo(env.getProperty(key)));
                });
    }

    @Test
    void shouldStartWhenUriKeysUseCamelCaseSpelling() {
        new ApplicationContextRunner().withUserConfiguration(TestConfigWithValidator.class)
                .withPropertyValues("user.security.loginPageURI=/custom/login.html").run(context -> {
                    assertThat(context).hasNotFailed();
                });
    }

    @Test
    void shouldFailStartupWhenUriKeyUsesKebabSpellingOnly() {
        new ApplicationContextRunner().withUserConfiguration(TestConfigWithValidator.class)
                .withPropertyValues("user.security.change-password-uri=/custom/changePassword").run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause()
                            .hasMessageContaining("user.security.changePasswordURI")
                            .hasMessageContaining("camelCase");
                });
    }
}
