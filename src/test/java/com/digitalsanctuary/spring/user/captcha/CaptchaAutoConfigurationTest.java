package com.digitalsanctuary.spring.user.captcha;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.digitalsanctuary.cf.turnstile.config.TurnstileConfigProperties;
import com.digitalsanctuary.cf.turnstile.service.TurnstileValidationService;


@DisplayName("CaptchaAutoConfiguration")
class CaptchaAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CaptchaAutoConfiguration.class));

    /** A fully usable Turnstile provider: service bean present, secret and site key configured. */
    @Configuration(proxyBeanMethods = false)
    static class TurnstileServiceBeanConfiguration {
        @Bean
        TurnstileValidationService turnstileValidationService() {
            TurnstileValidationService mock = Mockito.mock(TurnstileValidationService.class);
            Mockito.when(mock.getTurnstileSitekey()).thenReturn("real-site-key");
            return mock;
        }

        @Bean
        TurnstileConfigProperties turnstileConfigProperties() {
            TurnstileConfigProperties properties = new TurnstileConfigProperties();
            properties.setSecret("real-secret");
            properties.setSitekey("real-site-key");
            return properties;
        }
    }

    /** Turnstile present but unusable: no secret configured, so nothing can ever validate. */
    @Configuration(proxyBeanMethods = false)
    static class UnusableTurnstileConfiguration {
        @Bean
        TurnstileValidationService turnstileValidationService() {
            TurnstileValidationService mock = Mockito.mock(TurnstileValidationService.class);
            Mockito.when(mock.getTurnstileSitekey()).thenReturn("real-site-key");
            return mock;
        }

        @Bean
        TurnstileConfigProperties turnstileConfigProperties() {
            TurnstileConfigProperties properties = new TurnstileConfigProperties();
            properties.setSitekey("real-site-key");
            return properties;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TestCredentialTurnstileServiceConfiguration {
        @Bean
        TurnstileValidationService turnstileValidationService() {
            TurnstileValidationService mock = Mockito.mock(TurnstileValidationService.class);
            Mockito.when(mock.isUsingTestCredentials()).thenReturn(true);
            Mockito.when(mock.getTurnstileSitekey()).thenReturn("1x00000000000000000000AA");
            return mock;
        }

        @Bean
        TurnstileConfigProperties turnstileConfigProperties() {
            TurnstileConfigProperties properties = new TurnstileConfigProperties();
            properties.setSecret("1x0000000000000000000000000000000AA");
            properties.setSitekey("1x00000000000000000000AA");
            return properties;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomCaptchaServiceConfiguration {
        @Bean
        CaptchaService customCaptchaService() {
            return new CaptchaService() {
                @Override
                public CaptchaVerification verify(CaptchaContext context) {
                    return CaptchaVerification.verified();
                }

                @Override
                public Optional<String> siteKey() {
                    return Optional.of("custom");
                }
            };
        }
    }

    @Test
    void shouldRegisterNoInterceptorOrProviderBeansWhenDisabled() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(CaptchaService.class);
            assertThat(context).doesNotHaveBean(CaptchaValidationInterceptor.class);
            // CaptchaConfigProperties and CaptchaStartupValidator do register unconditionally; the
            // validator early-returns when disabled. Asserted so the "no beans at all" reading of
            // the disabled state doesn't creep back into the docs.
            assertThat(context).hasSingleBean(CaptchaStartupValidator.class);
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
                    // @PostConstruct validation surfaces wrapped in BeanCreationException.
                    assertThat(context.getStartupFailure()).rootCause()
                            .isInstanceOf(IllegalStateException.class).hasMessageContaining("CaptchaService");
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
                    assertThat(context.getBean(CaptchaService.class).siteKey()).contains("custom");
                });
    }

    @Test
    void shouldFailStartupWhenProviderCannotVerifyAnything() {
        // The likeliest production misconfiguration: the library is present and a CaptchaService
        // resolves, but no secret is set. Without this check the app starts clean and then rejects
        // 100% of registrations, resets, and resends with no indication why.
        contextRunner.withUserConfiguration(UnusableTurnstileConfiguration.class)
                .withPropertyValues("user.security.captcha.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause()
                            .isInstanceOf(IllegalStateException.class).hasMessageContaining("secret");
                });
    }

    @Test
    void shouldStartWithUnusableProviderWhenExplicitlyAllowed() {
        contextRunner.withUserConfiguration(UnusableTurnstileConfiguration.class)
                .withPropertyValues("user.security.captcha.enabled=true",
                        "user.security.captcha.allow-unusable-provider=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(CaptchaService.class);
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
