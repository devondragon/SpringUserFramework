package com.digitalsanctuary.spring.user.captcha;

import java.io.IOException;

import org.apache.commons.text.StringEscapeUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Rejects POSTs to the framework's unauthenticated email-sending API actions unless they carry a
 * valid CAPTCHA token. Registered by {@link CaptchaAutoConfiguration} only when
 * {@code user.security.captcha.enabled=true}, against exactly the three protected paths.
 *
 * <p>
 * The token is read from the {@value #TOKEN_HEADER} request header, falling back to the
 * {@value #TOKEN_PARAMETER} request parameter. The endpoints consume JSON request bodies, so the
 * token cannot travel in the body; client code must send it in the header or query string.
 * </p>
 *
 * <p>
 * Rejections are written directly as a {@code JSONResponse}-shaped body (HTTP 403, code
 * {@value #ERROR_CODE_CAPTCHA_FAILED}) so the consuming application's client JS renders them the
 * same way as other API errors. Fail-closed: a missing token, an unavailable provider, or a failed
 * validation all reject the request before the handler runs, so no email is sent.
 * </p>
 */
@Slf4j
@RequiredArgsConstructor
public class CaptchaValidationInterceptor implements HandlerInterceptor {

    /** Path of the registration API action. */
    public static final String REGISTRATION_PATH = "/user/registration";

    /** Path of the password-reset API action. */
    public static final String RESET_PASSWORD_PATH = "/user/resetPassword";

    /** Path of the resend-verification-token API action. */
    public static final String RESEND_TOKEN_PATH = "/user/resendRegistrationToken";

    /** Request header carrying the CAPTCHA response token (checked first). */
    public static final String TOKEN_HEADER = "X-Captcha-Token";

    /** Request parameter carrying the CAPTCHA response token (fallback). */
    public static final String TOKEN_PARAMETER = "cf-turnstile-response";

    /** JSONResponse code for CAPTCHA validation failures. */
    public static final int ERROR_CODE_CAPTCHA_FAILED = 8;

    private static final String MESSAGE_KEY = "message.captcha.validation-failed";
    private static final String DEFAULT_MESSAGE = "CAPTCHA verification failed. Please complete the challenge and try again.";

    /*
     * The action patterns compiled with the same PathPatternParser the InterceptorRegistry uses
     * (MappedInterceptor defaults to PathPatternParser.defaultInstance). Enforcement MUST use the
     * same pattern engine as registration: PathPattern matches against the parsed request path,
     * whose segments are URL-decoded and stripped of matrix parameters, so raw-URI string
     * comparison would let variants like "/user/registration;jsessionid=x" or
     * "/user/%72egistration" through unprotected while still reaching the handler.
     */
    private static final PathPattern REGISTRATION_PATTERN = PathPatternParser.defaultInstance.parse(REGISTRATION_PATH);
    private static final PathPattern RESET_PASSWORD_PATTERN = PathPatternParser.defaultInstance.parse(RESET_PASSWORD_PATH);
    private static final PathPattern RESEND_TOKEN_PATTERN = PathPatternParser.defaultInstance.parse(RESEND_TOKEN_PATH);

    private final CaptchaConfigProperties captchaConfigProperties;
    private final ObjectProvider<CaptchaService> captchaServiceProvider;
    private final MessageSource messages;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        if (!isActionProtected(request)) {
            return true;
        }
        String token = resolveToken(request);
        if (token == null || token.isBlank()) {
            log.warn("CAPTCHA token missing on {} from {}. Rejecting request.", path, request.getRemoteAddr());
            return reject(request, response);
        }
        CaptchaService captchaService = captchaServiceProvider.getIfAvailable();
        if (captchaService == null) {
            log.error("CAPTCHA is enabled but no CaptchaService is available. Failing closed for {}.", path);
            return reject(request, response);
        }
        if (!captchaService.verify(token, request)) {
            log.warn("CAPTCHA validation failed on {} from {}. Rejecting request.", path, request.getRemoteAddr());
            return reject(request, response);
        }
        return true;
    }

    /**
     * Decides whether this POST targets a protected action, matching the context-relative request
     * path with the same {@code PathPattern} engine the interceptor registration uses, so this
     * decision cannot disagree with the registration's. Fail-closed on both edge cases: a request
     * path that cannot be parsed, or one that matches none of the action patterns even though this
     * interceptor was invoked for it (the interceptor is registered against exactly these
     * patterns, so through the real dispatcher that cannot happen), is treated as protected.
     */
    private boolean isActionProtected(HttpServletRequest request) {
        PathContainer path;
        try {
            path = resolvePathWithinApplication(request);
        } catch (RuntimeException e) {
            log.warn("Could not parse request path {}. Failing closed.", request.getRequestURI(), e);
            return true;
        }
        CaptchaConfigProperties.Protect protect = captchaConfigProperties.getProtect();
        if (REGISTRATION_PATTERN.matches(path)) {
            return protect.isRegistration();
        }
        if (RESET_PASSWORD_PATTERN.matches(path)) {
            return protect.isResetPassword();
        }
        if (RESEND_TOKEN_PATTERN.matches(path)) {
            return protect.isResendRegistrationToken();
        }
        return true;
    }

    /**
     * Returns the context-relative request path exactly as Spring's {@code PathPattern} engine saw
     * it for handler mapping and interceptor matching: the {@code RequestPath} the
     * {@code DispatcherServlet} parsed and cached before invoking interceptors, or an identical
     * fresh parse of the request when no cached path exists (e.g. direct unit-test invocation).
     */
    private PathContainer resolvePathWithinApplication(HttpServletRequest request) {
        if (ServletRequestPathUtils.hasParsedRequestPath(request)) {
            return ServletRequestPathUtils.getParsedRequestPath(request).pathWithinApplication();
        }
        return ServletRequestPathUtils.parse(request).pathWithinApplication();
    }

    private String resolveToken(HttpServletRequest request) {
        String token = request.getHeader(TOKEN_HEADER);
        if (token == null || token.isBlank()) {
            token = request.getParameter(TOKEN_PARAMETER);
        }
        return token;
    }

    private boolean reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String message = messages.getMessage(MESSAGE_KEY, null, DEFAULT_MESSAGE, request.getLocale());
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"success\":false,\"redirectUrl\":null,\"code\":" + ERROR_CODE_CAPTCHA_FAILED
                + ",\"messages\":[\"" + StringEscapeUtils.escapeJson(message) + "\"],\"data\":null}");
        return false;
    }
}
