package com.digitalsanctuary.spring.user.security;

import static org.assertj.core.api.Assertions.assertThat;

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
    void flatFieldInitializersMatchShippedFile() throws Exception {
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
    void passwordFieldInitializersMatchShippedFile() throws Exception {
        Properties p = shipped();
        PasswordPolicyConfigProperties bean = new PasswordPolicyConfigProperties();
        assertThat(String.valueOf(bean.getMinLength())).isEqualTo(p.getProperty("user.security.password.min-length"));
        assertThat(String.valueOf(bean.getHistoryCount()))
                .isEqualTo(p.getProperty("user.security.password.history-count"));
        assertThat(String.valueOf(bean.getSimilarityThreshold()))
                .isEqualTo(p.getProperty("user.security.password.similarity-threshold"));
    }

    @Test
    void bindingTheShippedFileYieldsTheSameValuesAsTheInitializers() throws Exception {
        MockEnvironment env = new MockEnvironment();
        new ResourcePropertySource(new ClassPathResource("config/dsspringuserconfig.properties")).getSource()
                .forEach((k, v) -> env.setProperty(k, String.valueOf(v)));
        UserSecurityConfigProperties bound = Binder.get(env)
                .bind("user.security", UserSecurityConfigProperties.class).get();
        assertThat(bound.getLoginPageUri()).isEqualTo(new UserSecurityConfigProperties().getLoginPageUri());
        assertThat(bound.getBcryptStrength()).isEqualTo(new UserSecurityConfigProperties().getBcryptStrength());
    }
}
