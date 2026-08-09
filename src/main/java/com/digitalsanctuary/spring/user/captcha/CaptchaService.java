package com.digitalsanctuary.spring.user.captcha;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Provider-neutral CAPTCHA verification SPI.
 *
 * <p>
 * The framework ships a Cloudflare Turnstile implementation ({@link TurnstileCaptchaService}),
 * auto-configured when {@code com.digitalsanctuary:ds-spring-cf-turnstile} is on the classpath and
 * {@code user.security.captcha.provider=turnstile}. Consumers may register their own
 * {@code CaptchaService} bean to plug in a different provider; a consumer-supplied bean takes
 * precedence over the built-in one.
 * </p>
 */
public interface CaptchaService {

	/**
	 * Verifies a CAPTCHA response token. Implementations MUST fail closed: any error (missing
	 * configuration, provider unreachable, invalid token) returns {@code false}.
	 *
	 * @param token the CAPTCHA response token supplied by the client
	 * @param request the current request, for client IP extraction
	 * @return true only if the provider positively verified the token
	 */
	boolean verify(String token, HttpServletRequest request);

	/**
	 * Returns the public site key for rendering the CAPTCHA widget, or null if not configured.
	 *
	 * @return the public site key, or null
	 */
	String getSiteKey();

	/**
	 * Returns human-readable warnings about the current provider configuration (for example,
	 * always-pass test credentials). Logged at WARN during startup when CAPTCHA is enabled.
	 *
	 * @return warnings to log at startup; empty when the configuration looks production-ready
	 */
	default List<String> configurationWarnings() {
		return List.of();
	}
}
