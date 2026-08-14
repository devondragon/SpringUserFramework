package com.digitalsanctuary.spring.user.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.RecordComponent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;

import com.digitalsanctuary.spring.user.security.UserSecurityConfigProperties;

@DisplayName("UserSecurityUriControllerAdvice")
class UserSecurityUriControllerAdviceTest {

    @Controller
    static class TestPageController {
        @GetMapping("/user-security-advice-test-page")
        public String page() {
            return "test";
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(UserSecurityConfigProperties.class)
    static class PropertiesOnlyConfig {
    }

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesOnlyConfig.class, UserSecurityUriControllerAdvice.class);

    @Test
    void shouldExposeUserSecurityViewWhenHandlingControllerRequest() throws Exception {
        UserSecurityConfigProperties props = new UserSecurityConfigProperties();
        UserSecurityUriControllerAdvice advice = new UserSecurityUriControllerAdvice(props, "2020");
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new TestPageController())
                .setControllerAdvice(advice).build();

        mockMvc.perform(get("/user-security-advice-test-page")).andExpect(status().isOk())
                .andExpect(model().attributeExists("userSecurity"));
    }

    @Test
    void shouldMapEveryConfigUriWhenBuildingView() {
        // Distinct sentinel per field so a transposition anywhere in the 18-argument constructor call fails.
        UserSecurityConfigProperties props = new UserSecurityConfigProperties();
        props.setLoginPageUri("/s/loginPage");
        props.setLoginActionUri("/s/loginAction");
        props.setLoginSuccessUri("/s/loginSuccess");
        props.setLogoutActionUri("/s/logoutAction");
        props.setLogoutSuccessUri("/s/logoutSuccess");
        props.setRegistrationUri("/s/registration");
        props.setRegistrationPendingUri("/s/registrationPending");
        props.setRegistrationSuccessUri("/s/registrationSuccess");
        props.setRegistrationNewVerificationUri("/s/registrationNewVerification");
        props.setRegistrationConfirmUri("/s/registrationConfirm");
        props.setForgotPasswordUri("/s/forgotPassword");
        props.setForgotPasswordPendingUri("/s/forgotPasswordPending");
        props.setForgotPasswordChangeUri("/s/forgotPasswordChange");
        props.setUpdateUserUri("/s/updateUser");
        props.setUpdatePasswordUri("/s/updatePassword");
        props.setDeleteAccountUri("/s/deleteAccount");
        props.setChangePasswordUri("/s/changePassword");

        UserSecurityUriView view = new UserSecurityUriControllerAdvice(props, "1999").userSecurity();

        assertThat(view.loginPageUri()).isEqualTo("/s/loginPage");
        assertThat(view.loginActionUri()).isEqualTo("/s/loginAction");
        assertThat(view.loginSuccessUri()).isEqualTo("/s/loginSuccess");
        assertThat(view.logoutActionUri()).isEqualTo("/s/logoutAction");
        assertThat(view.logoutSuccessUri()).isEqualTo("/s/logoutSuccess");
        assertThat(view.registrationUri()).isEqualTo("/s/registration");
        assertThat(view.registrationPendingUri()).isEqualTo("/s/registrationPending");
        assertThat(view.registrationSuccessUri()).isEqualTo("/s/registrationSuccess");
        assertThat(view.registrationNewVerificationUri()).isEqualTo("/s/registrationNewVerification");
        assertThat(view.registrationConfirmUri()).isEqualTo("/s/registrationConfirm");
        assertThat(view.forgotPasswordUri()).isEqualTo("/s/forgotPassword");
        assertThat(view.forgotPasswordPendingUri()).isEqualTo("/s/forgotPasswordPending");
        assertThat(view.forgotPasswordChangeUri()).isEqualTo("/s/forgotPasswordChange");
        assertThat(view.updateUserUri()).isEqualTo("/s/updateUser");
        assertThat(view.updatePasswordUri()).isEqualTo("/s/updatePassword");
        assertThat(view.deleteAccountUri()).isEqualTo("/s/deleteAccount");
        assertThat(view.changePasswordUri()).isEqualTo("/s/changePassword");
        assertThat(view.copyrightFirstYear()).isEqualTo("1999");
    }

    @Test
    void shouldNotExposeSecretsWhenConfigContainsThem() throws Exception {
        UserSecurityConfigProperties props = new UserSecurityConfigProperties();
        String sentinelSecret = "sentinel-token-hash-secret-value";
        props.setTokenHashSecret(sentinelSecret);

        UserSecurityUriView view = new UserSecurityUriControllerAdvice(props, "2020").userSecurity();

        // The view must hold only plain strings, and none of them may carry the secret's value — a
        // component-name check alone would miss a renamed field that still holds the secret.
        for (RecordComponent component : UserSecurityUriView.class.getRecordComponents()) {
            assertThat(component.getType()).isEqualTo(String.class);
            Object value = component.getAccessor().invoke(view);
            assertThat(value).isNotEqualTo(sentinelSecret);
        }
    }

    @Test
    void shouldRegisterAdviceWhenExposeUrisToModelUnset() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(UserSecurityUriControllerAdvice.class));
    }

    @Test
    void shouldNotRegisterAdviceWhenExposeUrisToModelFalse() {
        contextRunner.withPropertyValues("user.security.expose-uris-to-model=false")
                .run(context -> assertThat(context).doesNotHaveBean(UserSecurityUriControllerAdvice.class));
    }

    @Test
    void shouldNotRegisterAdviceWhenExposeUrisToModelFalseWithCamelCaseSpelling() {
        // Relaxed matching of @ConditionalOnProperty needs the attached ConfigurationPropertySources, exactly as
        // SpringApplication provides in a real boot.
        contextRunner
                .withInitializer(context -> org.springframework.boot.context.properties.source.ConfigurationPropertySources
                        .attach(context.getEnvironment()))
                .withPropertyValues("user.security.exposeUrisToModel=false")
                .run(context -> assertThat(context).doesNotHaveBean(UserSecurityUriControllerAdvice.class));
    }
}
