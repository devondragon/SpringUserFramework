package com.digitalsanctuary.spring.user.captcha;

import java.util.Objects;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Everything a {@link CaptchaService} needs to verify one request.
 *
 * <p>
 * Passing a context rather than a widening parameter list means new information can be added for
 * providers that need it without breaking existing implementations — this is a published library
 * SPI, so a signature change after release is a breaking change for every consumer.
 * </p>
 *
 * @param action which protected API action is being verified; never null. Providers that bind a
 *        token to the challenge it was issued for (reCAPTCHA v3 actions) or apply per-action score
 *        thresholds should use this.
 * @param token the CAPTCHA response token supplied by the client; never null or blank (the
 *        framework rejects missing tokens before calling the provider)
 * @param remoteIp the resolved client IP to report to the provider, or null when it could not be
 *        determined. Resolved once by the framework so provider calls and rejection logs agree on
 *        who the client is.
 * @param request the current request, for providers needing details not surfaced above; never null
 */
public record CaptchaContext(CaptchaAction action, String token, String remoteIp, HttpServletRequest request) {

    /**
     * Canonical constructor.
     *
     * @param action which protected API action is being verified; must not be null
     * @param token the CAPTCHA response token; must not be null
     * @param remoteIp the resolved client IP, may be null
     * @param request the current request; must not be null
     */
    public CaptchaContext {
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(token, "token must not be null");
        Objects.requireNonNull(request, "request must not be null");
    }
}
