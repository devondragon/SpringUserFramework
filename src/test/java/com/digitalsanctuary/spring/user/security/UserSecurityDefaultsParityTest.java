package com.digitalsanctuary.spring.user.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.ResourcePropertySource;
import org.springframework.mock.env.MockEnvironment;

@DisplayName("user.security defaults parity with shipped properties file")
class UserSecurityDefaultsParityTest {

    private Properties shipped() throws Exception {
        ResourcePropertySource source =
                new ResourcePropertySource(new ClassPathResource("config/dsspringuserconfig.properties"));
        Properties props = new Properties();
        source.getSource().forEach(props::put);
        return props;
    }

    @Test
    void shouldMatchShippedFileWhenBindingFlatFields() throws Exception {
        Properties p = shipped();
        UserSecurityConfigProperties bean = new UserSecurityConfigProperties();
        assertThat(bean.getLoginPageUri()).isEqualTo(p.getProperty("user.security.loginPageURI"));
        assertThat(bean.getRegistrationUri()).isEqualTo(p.getProperty("user.security.registrationURI"));
        assertThat(bean.getChangePasswordUri()).isEqualTo(p.getProperty("user.security.changePasswordURI"));
        assertThat(bean.getDefaultAction()).isEqualTo(p.getProperty("user.security.defaultAction"));
        assertThat(String.valueOf(bean.getBcryptStrength())).isEqualTo(p.getProperty("user.security.bcryptStrength"));
        assertThat(String.valueOf(bean.getFailedLoginAttempts()))
                .isEqualTo(p.getProperty("user.security.failedLoginAttempts"));
    }

    @Test
    void shouldMatchShippedFileWhenBindingPasswordFields() throws Exception {
        Properties p = shipped();
        PasswordPolicyConfigProperties bean = new PasswordPolicyConfigProperties();
        assertThat(String.valueOf(bean.getMinLength())).isEqualTo(p.getProperty("user.security.password.min-length"));
        assertThat(String.valueOf(bean.getHistoryCount()))
                .isEqualTo(p.getProperty("user.security.password.history-count"));
        assertThat(String.valueOf(bean.getSimilarityThreshold()))
                .isEqualTo(p.getProperty("user.security.password.similarity-threshold"));
    }

    @Test
    void shouldEqualInitializersWhenBindingShippedFile() throws Exception {
        MockEnvironment env = new MockEnvironment();
        new ResourcePropertySource(new ClassPathResource("config/dsspringuserconfig.properties")).getSource()
                .forEach((k, v) -> env.setProperty(k, String.valueOf(v)));
        UserSecurityConfigProperties bound = Binder.get(env)
                .bind("user.security", UserSecurityConfigProperties.class).get();
        assertThat(bound.getLoginPageUri()).isEqualTo(new UserSecurityConfigProperties().getLoginPageUri());
        assertThat(bound.getBcryptStrength()).isEqualTo(new UserSecurityConfigProperties().getBcryptStrength());
    }

    @Test
    void shouldMatchShippedFileWhenBindingRememberMeFields() throws Exception {
        Properties p = shipped();
        RememberMeConfigProperties bean = new RememberMeConfigProperties();
        assertThat(String.valueOf(bean.isEnabled())).isEqualTo(p.getProperty("user.security.rememberMe.enabled"));
        assertThat(String.valueOf(bean.getTokenValiditySeconds()))
                .isEqualTo(p.getProperty("user.security.rememberMe.tokenValiditySeconds"));
        assertThat(bean.getRememberMeParameter()).isEqualTo(p.getProperty("user.security.rememberMe.rememberMeParameter"));
        assertThat(bean.getRememberMeCookieName()).isEqualTo(p.getProperty("user.security.rememberMe.rememberMeCookieName"));
        assertThat(String.valueOf(bean.isUsePersistentTokens()))
                .isEqualTo(p.getProperty("user.security.rememberMe.usePersistentTokens"));
    }

    @Test
    void shouldMatchShippedFileWhenBindingUriListFields() throws Exception {
        Properties p = shipped();
        UserSecurityConfigProperties bean = new UserSecurityConfigProperties();

        List<String> expectedUnprotectedUris = splitAndTrim(p.getProperty("user.security.unprotectedURIs"));
        assertThat(bean.getUnprotectedUris()).isEqualTo(expectedUnprotectedUris);

        List<String> expectedProtectedUris = splitAndTrim(p.getProperty("user.security.protectedURIs"));
        assertThat(bean.getProtectedUris()).isEqualTo(expectedProtectedUris);

        List<String> expectedDisableCsrfUris = splitAndTrim(p.getProperty("user.security.disableCSRFURIs"));
        assertThat(expectedDisableCsrfUris).isEmpty();
        assertThat(bean.getDisableCsrfUris()).isEmpty();
    }

    private static List<String> splitAndTrim(String value) {
        List<String> result = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return result;
        }
        for (String segment : value.split(",")) {
            String trimmed = segment.trim();
            if (!trimmed.isBlank()) {
                result.add(trimmed);
            }
        }
        return result;
    }
}
