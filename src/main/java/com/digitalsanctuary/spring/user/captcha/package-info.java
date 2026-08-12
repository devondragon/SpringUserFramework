/**
 * Optional CAPTCHA protection for the framework's unauthenticated, email-sending API actions.
 *
 * <p>
 * Disabled by default. When {@code user.security.captcha.enabled=true}, a
 * {@link com.digitalsanctuary.spring.user.captcha.CaptchaValidationInterceptor} requires a valid
 * CAPTCHA token on every {@link com.digitalsanctuary.spring.user.captcha.CaptchaAction} whose
 * per-action toggle is on, rejecting anything else with HTTP 403 before the handler runs — so no
 * account is created and no email is sent.
 * </p>
 *
 * <h2>Extension point</h2>
 *
 * <p>
 * {@link com.digitalsanctuary.spring.user.captcha.CaptchaService} is the provider SPI. The
 * framework ships a Cloudflare Turnstile implementation, auto-configured when the optional
 * {@code com.digitalsanctuary:ds-spring-cf-turnstile} dependency is present; a consumer-supplied
 * {@code CaptchaService} bean replaces it. Implementations report a
 * {@link com.digitalsanctuary.spring.user.captcha.CaptchaVerification} rather than a boolean, so
 * the framework — not the implementation — decides that an unreachable or misconfigured provider
 * rejects the request.
 * </p>
 *
 * <h2>Fail-closed</h2>
 *
 * <p>
 * Every uncertain state denies the request: a missing or blank token, no resolvable provider, a
 * provider error, a provider that throws or returns null, an unparseable path, and a path matching
 * no known action. Startup fails outright when CAPTCHA is enabled but no provider resolves, or when
 * the provider reports it cannot verify anything (see
 * {@link com.digitalsanctuary.spring.user.captcha.CaptchaService#configurationErrors()}), because
 * both states would otherwise produce an application that looks healthy while rejecting every
 * protected request.
 * </p>
 *
 * @see com.digitalsanctuary.spring.user.captcha.CaptchaService
 * @see com.digitalsanctuary.spring.user.captcha.CaptchaAction
 * @see com.digitalsanctuary.spring.user.captcha.CaptchaConfigProperties
 */
package com.digitalsanctuary.spring.user.captcha;
