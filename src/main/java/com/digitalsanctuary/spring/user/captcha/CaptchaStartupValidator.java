package com.digitalsanctuary.spring.user.captcha;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Validates the CAPTCHA configuration at startup.
 *
 * <p>
 * Fails application startup (fail closed) when {@code user.security.captcha.enabled=true} but no
 * {@link CaptchaService} can be resolved — for example, the configured provider's library is not on
 * the classpath, or {@code user.security.captcha.provider} names an unknown provider. Also logs any
 * provider configuration warnings (such as Cloudflare always-pass test keys) so test credentials
 * cannot reach production unnoticed.
 * </p>
 */
@Slf4j
@RequiredArgsConstructor
public class CaptchaStartupValidator {

    private final CaptchaConfigProperties captchaConfigProperties;
    private final ObjectProvider<CaptchaService> captchaServiceProvider;

    /**
     * Validates CAPTCHA configuration once the context is fully refreshed.
     *
     * @param event the context refreshed event
     */
    @EventListener(ContextRefreshedEvent.class)
    public void validateCaptchaConfiguration(ContextRefreshedEvent event) {
        if (!captchaConfigProperties.isEnabled()) {
            return;
        }
        CaptchaService captchaService = captchaServiceProvider.getIfAvailable();
        if (captchaService == null) {
            throw new IllegalStateException("user.security.captcha.enabled=true but no CaptchaService is available"
                    + " for provider '" + captchaConfigProperties.getProvider() + "'. Add"
                    + " com.digitalsanctuary:ds-spring-cf-turnstile to the classpath (provider 'turnstile'), supply"
                    + " your own CaptchaService bean, or set user.security.captcha.enabled=false. Refusing to start"
                    + " with CAPTCHA silently disabled (fail closed).");
        }
        CaptchaConfigProperties.Protect protect = captchaConfigProperties.getProtect();
        log.info("CAPTCHA protection enabled (provider: {}). Protected actions: registration={},"
                + " resetPassword={}, resendRegistrationToken={}", captchaConfigProperties.getProvider(),
                protect.isRegistration(), protect.isResetPassword(), protect.isResendRegistrationToken());
        for (String warning : captchaService.configurationWarnings()) {
            log.warn("========================================================");
            log.warn("CAPTCHA CONFIGURATION WARNING: {}", warning);
            log.warn("========================================================");
        }
    }
}
