package com.digitalsanctuary.spring.user.captcha;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.digitalsanctuary.cf.turnstile.service.TurnstileValidationService;

import jakarta.servlet.http.HttpServletRequest;

@DisplayName("CaptchaAutoConfiguration")
class CaptchaAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CaptchaAutoConfiguration.class));

    @Configuration(proxyBeanMethods = false)
    static class TurnstileServiceBeanConfiguration {
        @Bean
        TurnstileValidationService turnstileValidationService() {
            return Mockito.mock(TurnstileValidationService.class);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TestCredentialTurnstileServiceConfiguration {
        @Bean
        TurnstileValidationService turnstileValidationService() {
            TurnstileValidationService mock = Mockito.mock(TurnstileValidationService.class);
            Mockito.when(mock.isUsingTestCredentials()).thenReturn(true);
            return mock;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomCaptchaServiceConfiguration {
        @Bean
        CaptchaService customCaptchaService() {
            return new CaptchaService() {
                @Override
                public boolean verify(String token, HttpServletRequest request) {
                    return true;
                }

                @Override
                public String getSiteKey() {
                    return "custom";
                }
            };
        }
    }

    @Test
    void shouldRegisterNoCaptchaBeansWhenDisabled() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(CaptchaService.class);
            assertThat(context).doesNotHaveBean(CaptchaValidationInterceptor.class);
        });
    }

    @Test
    void shouldStartCleanlyWhenDisabledAndTurnstileClassAbsent() {
        contextRunner.withClassLoader(new FilteredClassLoader(TurnstileValidationService.class)).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(CaptchaService.class);
        });
    }

    @Test
    void shouldRegisterTurnstileCaptchaServiceAndInterceptorWhenEnabled() {
        contextRunner.withUserConfiguration(TurnstileServiceBeanConfiguration.class)
                .withPropertyValues("user.security.captcha.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(CaptchaService.class);
                    assertThat(context.getBean(CaptchaService.class)).isInstanceOf(TurnstileCaptchaService.class);
                    assertThat(context).hasSingleBean(CaptchaValidationInterceptor.class);
                    assertThat(context).hasSingleBean(CaptchaStartupValidator.class);
                });
    }

    @Test
    void shouldFailStartupWhenEnabledAndTurnstileClassAbsent() {
        contextRunner.withClassLoader(new FilteredClassLoader(TurnstileValidationService.class))
                .withPropertyValues("user.security.captcha.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("CaptchaService");
                });
    }

    @Test
    void shouldFailStartupWhenEnabledWithUnknownProvider() {
        contextRunner.withUserConfiguration(TurnstileServiceBeanConfiguration.class)
                .withPropertyValues("user.security.captcha.enabled=true", "user.security.captcha.provider=recaptcha")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldPreferConsumerSuppliedCaptchaServiceBean() {
        contextRunner
                .withUserConfiguration(CustomCaptchaServiceConfiguration.class,
                        TurnstileServiceBeanConfiguration.class)
                .withPropertyValues("user.security.captcha.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(CaptchaService.class);
                    assertThat(context.getBean(CaptchaService.class).getSiteKey()).isEqualTo("custom");
                });
    }

    @Test
    void shouldSurfaceTestCredentialWarningsAtStartup() {
        // The validator logs provider warnings; the warning content is proven by the adapter
        // test. Here we prove the wiring: a provider reporting test credentials surfaces a
        // warning through the auto-configured CaptchaService, and startup still succeeds.
        contextRunner.withUserConfiguration(TestCredentialTurnstileServiceConfiguration.class)
                .withPropertyValues("user.security.captcha.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    List<String> warnings = context.getBean(CaptchaService.class).configurationWarnings();
                    assertThat(warnings).anySatisfy(warning -> assertThat(warning).contains("test"));
                });
    }
}
