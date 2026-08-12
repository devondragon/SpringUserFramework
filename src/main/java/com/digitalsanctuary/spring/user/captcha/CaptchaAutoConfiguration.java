package com.digitalsanctuary.spring.user.captcha;

import java.util.Arrays;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.digitalsanctuary.cf.turnstile.config.TurnstileConfigProperties;
import com.digitalsanctuary.cf.turnstile.service.TurnstileValidationService;

import tools.jackson.databind.ObjectMapper;

/**
 * Auto-configuration for optional CAPTCHA verification on the framework's unauthenticated,
 * email-sending API actions (registration, password reset, resend verification token).
 *
 * <p>
 * Unless {@code user.security.captcha.enabled=true}, no interceptor is registered and no provider
 * beans are created, so no request is ever inspected and existing consumers see no behavior change.
 * Only the {@link CaptchaConfigProperties} binding and {@link CaptchaStartupValidator} are always
 * registered, and the validator is a no-op when disabled. The Turnstile provider additionally
 * requires {@code com.digitalsanctuary:ds-spring-cf-turnstile} on the classpath; the framework
 * itself only depends on it at compile time.
 * </p>
 */
@AutoConfiguration
@EnableConfigurationProperties(CaptchaConfigProperties.class)
public class CaptchaAutoConfiguration {

    /**
     * The startup validator is registered unconditionally — regardless of the enabled flag and of
     * whether any provider library is on the classpath — so enabling CAPTCHA without a provider
     * fails fast instead of silently not protecting anything. It early-returns when CAPTCHA is
     * disabled.
     *
     * @param captchaConfigProperties the captcha configuration properties
     * @param captchaServiceProvider provider for the (possibly absent) captcha service
     * @return the startup validator
     */
    @Bean
    public CaptchaStartupValidator captchaStartupValidator(CaptchaConfigProperties captchaConfigProperties,
            ObjectProvider<CaptchaService> captchaServiceProvider) {
        return new CaptchaStartupValidator(captchaConfigProperties, captchaServiceProvider);
    }

    /**
     * Turnstile provider wiring. Guarded by classpath presence of the Turnstile library so the
     * framework runs without it; the adapter bean backs off to any consumer-supplied
     * {@link CaptchaService}.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(TurnstileValidationService.class)
    @ConditionalOnProperty(name = "user.security.captcha.enabled", havingValue = "true")
    static class TurnstileCaptchaConfiguration {

        /**
         * The Turnstile-backed captcha service.
         *
         * @param turnstileServiceProvider provider for the Turnstile validation service bean
         * @return the captcha service
         */
        @Bean
        @ConditionalOnMissingBean(CaptchaService.class)
        @ConditionalOnProperty(name = "user.security.captcha.provider", havingValue = "turnstile",
                matchIfMissing = true)
        public CaptchaService captchaService(ObjectProvider<TurnstileValidationService> turnstileServiceProvider,
                ObjectProvider<TurnstileConfigProperties> turnstilePropertiesProvider) {
            return new TurnstileCaptchaService(turnstileServiceProvider, turnstilePropertiesProvider);
        }
    }

    /**
     * Interceptor registration, active only when CAPTCHA is enabled.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "user.security.captcha.enabled", havingValue = "true")
    static class CaptchaWebConfiguration {

        /**
         * The captcha validation interceptor.
         *
         * @param captchaConfigProperties the captcha configuration properties
         * @param captchaServiceProvider provider for the (possibly absent) captcha service
         * @param messages the message source for localized rejection messages
         * @return the interceptor
         */
        @Bean
        public CaptchaValidationInterceptor captchaValidationInterceptor(
                CaptchaConfigProperties captchaConfigProperties,
                ObjectProvider<CaptchaService> captchaServiceProvider, MessageSource messages,
                ObjectProvider<ObjectMapper> objectMapperProvider, ApplicationEventPublisher eventPublisher) {
            return new CaptchaValidationInterceptor(captchaConfigProperties, captchaServiceProvider, messages,
                    objectMapperProvider, eventPublisher);
        }

        /**
         * Registers the interceptor against exactly the {@link CaptchaAction} paths. Deriving the
         * patterns from the enum keeps registration and enforcement from drifting apart — a
         * mismatch there is a silent bypass.
         *
         * @param captchaValidationInterceptor the interceptor to register
         * @return the WebMvcConfigurer registering the interceptor
         */
        @Bean
        public WebMvcConfigurer captchaWebMvcConfigurer(CaptchaValidationInterceptor captchaValidationInterceptor) {
            String[] paths = Arrays.stream(CaptchaAction.values()).map(CaptchaAction::path).toArray(String[]::new);
            return new WebMvcConfigurer() {
                @Override
                public void addInterceptors(InterceptorRegistry registry) {
                    registry.addInterceptor(captchaValidationInterceptor).addPathPatterns(paths);
                }
            };
        }
    }
}
