package com.digitalsanctuary.spring.user.captcha;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;

import com.digitalsanctuary.cf.turnstile.config.TurnstileConfigProperties;
import com.digitalsanctuary.cf.turnstile.service.TurnstileValidationService;

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
 * auto-configuration was excluded) or Cloudflare cannot be reached, {@link #verify} reports
 * {@link CaptchaVerification.Outcome#ERROR} and the framework rejects the request.
 * Test-credential knowledge lives in the Turnstile library
 * ({@code TurnstileValidationService#isUsingTestCredentials()}); this adapter only surfaces it.
 * </p>
 */
@Slf4j
@RequiredArgsConstructor
public class TurnstileCaptchaService implements CaptchaService {

    private final ObjectProvider<TurnstileValidationService> turnstileServiceProvider;
    private final ObjectProvider<TurnstileConfigProperties> turnstilePropertiesProvider;

    @Override
    public CaptchaVerification verify(CaptchaContext context) {
        TurnstileValidationService turnstileService = turnstileServiceProvider.getIfAvailable();
        if (turnstileService == null) {
            log.error("CAPTCHA is enabled but no TurnstileValidationService bean is available. Failing closed.");
            return CaptchaVerification.error("no TurnstileValidationService bean available");
        }
        try {
            // The framework resolves the client IP once (see CaptchaValidationInterceptor) so the
            // address reported to Cloudflare matches the one in the rejection logs.
            if (turnstileService.validateTurnstileResponse(context.token(), context.remoteIp())) {
                return CaptchaVerification.verified();
            }
            return CaptchaVerification.rejected("Turnstile reported the token invalid");
        } catch (RuntimeException e) {
            log.error("Unexpected error during Turnstile verification. Failing closed.", e);
            return CaptchaVerification.error("Turnstile verification threw " + e.getClass().getSimpleName());
        }
    }

    @Override
    public Optional<String> siteKey() {
        TurnstileValidationService turnstileService = turnstileServiceProvider.getIfAvailable();
        if (turnstileService == null) {
            return Optional.empty();
        }
        try {
            String siteKey = turnstileService.getTurnstileSitekey();
            return (siteKey == null || siteKey.isBlank()) ? Optional.empty() : Optional.of(siteKey);
        } catch (RuntimeException e) {
            log.error("Error retrieving Turnstile site key.", e);
            return Optional.empty();
        }
    }

    @Override
    public List<String> configurationWarnings() {
        TurnstileValidationService turnstileService = turnstileServiceProvider.getIfAvailable();
        if (turnstileService == null) {
            // Reported as an error, not a warning — see configurationErrors().
            return List.of();
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
        if (turnstilePropertiesProvider.getIfAvailable() == null) {
            // A warning, not an error: a consumer who excludes the Turnstile auto-configuration and
            // hand-wires a working TurnstileValidationService legitimately has no properties bean.
            // We simply cannot verify their credentials, which is not the same as knowing they are
            // broken — failing startup here would block a working configuration.
            return List.of("Turnstile is enabled but no TurnstileConfigProperties bean is available, so its secret"
                    + " key could not be verified at startup. If token validation fails for every request, check"
                    + " that a secret key is configured.");
        }
        return List.of();
    }

    @Override
    public List<String> configurationErrors() {
        if (turnstileServiceProvider.getIfAvailable() == null) {
            return List.of("CAPTCHA is enabled with provider 'turnstile' but no TurnstileValidationService bean"
                    + " was found. Every CAPTCHA-protected request would be rejected. Ensure the"
                    + " ds-spring-cf-turnstile auto-configuration is active.");
        }
        List<String> errors = new ArrayList<>();
        TurnstileConfigProperties turnstileProperties = turnstilePropertiesProvider.getIfAvailable();
        if (turnstileProperties != null && isBlank(turnstileProperties.getSecret())) {
            // Without a secret every siteverify call fails, so every protected request is rejected
            // while the application otherwise looks healthy.
            errors.add("Turnstile is enabled but no secret key is configured (ds.cf.turnstile.secret). Token"
                    + " validation cannot succeed, so every CAPTCHA-protected request would be rejected.");
        }
        if (siteKey().isEmpty()) {
            // Without a site key the consuming app's widget renders nothing, users submit with no
            // token, and every protected request is rejected for a challenge never shown.
            errors.add("Turnstile is enabled but no site key is configured (ds.cf.turnstile.sitekey). The CAPTCHA"
                    + " widget cannot render, so every CAPTCHA-protected request would be rejected.");
        }
        return List.copyOf(errors);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
