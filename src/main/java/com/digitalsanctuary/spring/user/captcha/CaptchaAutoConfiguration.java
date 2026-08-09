package com.digitalsanctuary.spring.user.captcha;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.digitalsanctuary.cf.turnstile.service.TurnstileValidationService;

/**
 * Auto-configuration for optional CAPTCHA verification on the framework's unauthenticated,
 * email-sending API actions (registration, password reset, resend verification token).
 *
 * <p>
 * Entirely inert unless {@code user.security.captcha.enabled=true}: no interceptor is registered
 * and no provider beans are created, so existing consumers see no behavior change. The Turnstile
 * provider additionally requires {@code com.digitalsanctuary:ds-spring-cf-turnstile} on the
 * classpath; the framework itself only depends on it at compile time.
 * </p>
 */
@AutoConfiguration
@EnableConfigurationProperties(CaptchaConfigProperties.class)
public class CaptchaAutoConfiguration {

    /**
     * Startup validation runs whenever the library is present, so enabling CAPTCHA without a
     * provider fails fast instead of silently not protecting anything.
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
        public CaptchaService captchaService(ObjectProvider<TurnstileValidationService> turnstileServiceProvider) {
            return new TurnstileCaptchaService(turnstileServiceProvider);
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
                ObjectProvider<CaptchaService> captchaServiceProvider, MessageSource messages) {
            return new CaptchaValidationInterceptor(captchaConfigProperties, captchaServiceProvider, messages);
        }

        /**
         * Registers the interceptor against exactly the three protected API paths.
         *
         * @param captchaValidationInterceptor the interceptor to register
         * @return the WebMvcConfigurer registering the interceptor
         */
        @Bean
        public WebMvcConfigurer captchaWebMvcConfigurer(CaptchaValidationInterceptor captchaValidationInterceptor) {
            return new WebMvcConfigurer() {
                @Override
                public void addInterceptors(InterceptorRegistry registry) {
                    registry.addInterceptor(captchaValidationInterceptor).addPathPatterns(
                            CaptchaValidationInterceptor.REGISTRATION_PATH,
                            CaptchaValidationInterceptor.RESET_PASSWORD_PATH,
                            CaptchaValidationInterceptor.RESEND_TOKEN_PATH);
                }
            };
        }
    }
}
