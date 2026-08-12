package com.digitalsanctuary.spring.user.captcha;

import java.util.List;
import java.util.Optional;

/**
 * Provider-neutral CAPTCHA verification SPI.
 *
 * <p>
 * The framework ships a Cloudflare Turnstile implementation ({@code TurnstileCaptchaService}),
 * auto-configured when {@code user.security.captcha.enabled=true}, the
 * {@code com.digitalsanctuary:ds-spring-cf-turnstile} library is on the classpath, and
 * {@code user.security.captcha.provider} is {@code turnstile} (the default). Consumers may register
 * their own {@code CaptchaService} bean to plug in a different provider; a consumer-supplied bean
 * takes precedence over the built-in one.
 * </p>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * @Component
 * public class HCaptchaService implements CaptchaService {
 *     private final HCaptchaClient client;
 *
 *     @Override
 *     public CaptchaVerification verify(CaptchaContext context) {
 *         try {
 *             return client.siteverify(context.token(), context.remoteIp())
 *                     ? CaptchaVerification.verified()
 *                     : CaptchaVerification.rejected("hcaptcha reported the token invalid");
 *         } catch (IOException e) {
 *             // Report the failure; the framework decides that this rejects the request.
 *             return CaptchaVerification.error("hcaptcha unreachable: " + e.getMessage());
 *         }
 *     }
 * }
 * }</pre>
 *
 * <p>
 * <strong>Fail-closed:</strong> implementations never have to decide what an outage means. Report
 * {@link CaptchaVerification#error(String)} when verification could not be completed and the
 * framework rejects the request. Throwing is also safe — the framework catches any runtime
 * exception from {@link #verify(CaptchaContext)} and treats it as an error — but returning
 * {@code error(...)} produces a clearer log.
 * </p>
 *
 * <p>
 * <strong>Thread Safety:</strong> implementations must be thread-safe as they may be invoked
 * concurrently from multiple request threads.
 * </p>
 *
 * @see CaptchaContext
 * @see CaptchaVerification
 */
public interface CaptchaService {

    /**
     * Verifies a CAPTCHA response token.
     *
     * @param context the request being verified; never null, and its token is never blank
     * @return the outcome; never null. Only {@link CaptchaVerification.Outcome#VERIFIED} lets the
     *         request proceed.
     */
    CaptchaVerification verify(CaptchaContext context);

    /**
     * Returns the public site key for rendering the CAPTCHA widget.
     *
     * <p>
     * Called once per request for MVC page controllers (see {@code CaptchaSiteKeyControllerAdvice}),
     * so implementations should return a cached or configured value rather than performing I/O.
     * </p>
     *
     * @return the public site key, or empty when none is configured
     */
    default Optional<String> siteKey() {
        return Optional.empty();
    }

    /**
     * Returns human-readable warnings about the current provider configuration (for example,
     * always-pass test credentials). Logged at WARN during startup when CAPTCHA is enabled.
     *
     * @return warnings to log at startup; empty when the configuration looks production-ready
     */
    default List<String> configurationWarnings() {
        return List.of();
    }

    /**
     * Returns reasons this provider cannot verify anything — a missing credential, an absent
     * delegate bean, or any other state in which {@link #verify(CaptchaContext)} would reject
     * every request.
     *
     * <p>
     * Reported separately from {@link #configurationWarnings()} because the consequence is
     * different: a misconfigured provider does not degrade the service, it takes every protected
     * endpoint offline with a 403 while the application looks healthy. When CAPTCHA is enabled and
     * this returns anything, {@code CaptchaStartupValidator} fails startup unless
     * {@code user.security.captcha.allow-unusable-provider=true}.
     * </p>
     *
     * @return reasons the provider cannot work; empty when it is usable
     */
    default List<String> configurationErrors() {
        return List.of();
    }
}
