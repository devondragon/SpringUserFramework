package com.digitalsanctuary.spring.user.web;

import org.springframework.web.servlet.HandlerInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Adds privacy and no-cache response headers to the password-reset pages.
 *
 * <p>
 * The password-reset flow carries the reset token in the page URL (SUF-05 / CWE-598). This interceptor sets
 * {@code Referrer-Policy: no-referrer} so the token is not leaked in the {@code Referer} header when the page loads
 * sub-resources or navigates away, and {@code Cache-Control: no-store} so the token-bearing URL is not written to
 * shared or browser caches. It is a non-breaking mitigation: it does not change the token contract, so the reset flow
 * continues to work unchanged for existing consumers. Registered against the configured reset URIs by
 * {@link WebInterceptorConfig}.
 * </p>
 */
public class PasswordResetSecurityHeadersInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(final HttpServletRequest request, final HttpServletResponse response, final Object handler) {
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Cache-Control", "no-store");
        return true;
    }
}
