package com.digitalsanctuary.spring.user.captcha;

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;

import com.digitalsanctuary.cf.turnstile.service.TurnstileValidationService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link CaptchaService} adapter over the ds-spring-cf-turnstile library's
 * {@link TurnstileValidationService} (requires 2.1.0+).
 *
 * <p>
 * This is the only framework class that references Turnstile types. It is intentionally NOT a
 * scanned component: it is instantiated by {@link CaptchaAutoConfiguration} only when the Turnstile
 * library is on the classpath, so the framework loads and runs without it.
 * </p>
 *
 * <p>
 * Fail-closed: if the {@code TurnstileValidationService} bean is unavailable (for example its
 * auto-configuration was excluded) or Cloudflare cannot be reached, {@link #verify} returns false.
 * Test-credential knowledge lives in the Turnstile library
 * ({@code TurnstileValidationService#isUsingTestCredentials()}); this adapter only surfaces it.
 * </p>
 */
@Slf4j
@RequiredArgsConstructor
public class TurnstileCaptchaService implements CaptchaService {

    private final ObjectProvider<TurnstileValidationService> turnstileServiceProvider;

    @Override
    public boolean verify(String token, HttpServletRequest request) {
        TurnstileValidationService turnstileService = turnstileServiceProvider.getIfAvailable();
        if (turnstileService == null) {
            log.error("CAPTCHA is enabled but no TurnstileValidationService bean is available. Failing closed.");
            return false;
        }
        try {
            String clientIp = turnstileService.getClientIpAddress(request);
            return turnstileService.validateTurnstileResponse(token, clientIp);
        } catch (RuntimeException e) {
            log.error("Unexpected error during Turnstile verification. Failing closed.", e);
            return false;
        }
    }

    @Override
    public String getSiteKey() {
        TurnstileValidationService turnstileService = turnstileServiceProvider.getIfAvailable();
        if (turnstileService == null) {
            return null;
        }
        try {
            return turnstileService.getTurnstileSitekey();
        } catch (RuntimeException e) {
            log.error("Error retrieving Turnstile site key. Failing closed.", e);
            return null;
        }
    }

    @Override
    public List<String> configurationWarnings() {
        TurnstileValidationService turnstileService = turnstileServiceProvider.getIfAvailable();
        if (turnstileService == null) {
            return List.of("CAPTCHA is enabled with provider 'turnstile' but no TurnstileValidationService bean"
                    + " was found. All CAPTCHA-protected requests will be rejected (fail closed). Ensure the"
                    + " ds-spring-cf-turnstile auto-configuration is active.");
        }
        try {
            if (turnstileService.isUsingTestCredentials()) {
                return List.of("Turnstile is configured with Cloudflare test credentials. CAPTCHA validation is"
                        + " running in test mode and provides NO bot protection. Do not use this in production.");
            }
        } catch (RuntimeException e) {
            log.warn("Error querying Turnstile credential configuration. Provider could not be queried.", e);
            return List.of("Turnstile configuration could not be queried. Verify that the TurnstileValidationService"
                    + " bean is properly configured and accessible.");
        }
        return List.of();
    }
}
