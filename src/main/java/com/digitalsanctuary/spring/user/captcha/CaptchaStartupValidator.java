package com.digitalsanctuary.spring.user.captcha;

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Validates the CAPTCHA configuration at startup.
 *
 * <p>
 * Fails application startup (fail closed) when {@code user.security.captcha.enabled=true} and
 * either no {@link CaptchaService} can be resolved — the configured provider's library is not on
 * the classpath, or {@code user.security.captcha.provider} names an unknown provider — or the
 * resolved provider reports it cannot verify anything via
 * {@link CaptchaService#configurationErrors()} (a missing secret or site key, say). Both states
 * would otherwise produce an application that starts clean and then rejects every request to every
 * protected endpoint. Set {@code user.security.captcha.allow-unusable-provider=true} to downgrade
 * the second case to a loud ERROR banner. Also logs provider configuration warnings (such as
 * Cloudflare always-pass test keys) so test credentials cannot reach production unnoticed.
 * </p>
 */
@Slf4j
@RequiredArgsConstructor
public class CaptchaStartupValidator {

    private final CaptchaConfigProperties captchaConfigProperties;
    private final ObjectProvider<CaptchaService> captchaServiceProvider;

    /**
     * Validates CAPTCHA configuration as this bean initializes.
     *
     * <p>
     * Deliberately {@code @PostConstruct} rather than a {@code ContextRefreshedEvent} listener: a
     * consuming application that defines an {@code applicationEventMulticaster} with a
     * {@code taskExecutor} publishes context events on worker threads, where a thrown exception is
     * discarded and the fail-startup guarantee below would silently become a no-op. Bean
     * initialization is not interceptable that way.
     * </p>
     */
    @PostConstruct
    public void validateCaptchaConfiguration() {
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
        List<String> errors = captchaService.configurationErrors();
        if (!errors.isEmpty() && !captchaConfigProperties.isAllowUnusableProvider()) {
            throw new IllegalStateException("user.security.captcha.enabled=true but the '"
                    + captchaConfigProperties.getProvider() + "' provider cannot verify anything, so every request to"
                    + " every CAPTCHA-protected endpoint would be rejected: " + String.join(" ", errors)
                    + " Fix the configuration, set user.security.captcha.enabled=false, or set"
                    + " user.security.captcha.allow-unusable-provider=true to start anyway.");
        }
        CaptchaConfigProperties.Protect protect = captchaConfigProperties.getProtect();
        log.info("CAPTCHA protection enabled (provider: {}). Protected actions: registration={},"
                + " passwordlessRegistration={}, resetPassword={}, resendRegistrationToken={}",
                captchaConfigProperties.getProvider(), protect.isRegistration(),
                protect.isPasswordlessRegistration(), protect.isResetPassword(),
                protect.isResendRegistrationToken());
        for (String error : errors) {
            // Only reachable with allow-unusable-provider=true; the consumer opted into booting
            // with a provider that rejects everything, so make it as loud as possible.
            log.error("========================================================");
            log.error("CAPTCHA PROVIDER UNUSABLE: {}", error);
            log.error("Every CAPTCHA-protected request will be rejected.");
            log.error("========================================================");
        }
        for (String warning : captchaService.configurationWarnings()) {
            log.warn("========================================================");
            log.warn("CAPTCHA CONFIGURATION WARNING: {}", warning);
            log.warn("========================================================");
        }
    }
}
