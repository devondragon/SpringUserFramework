package com.digitalsanctuary.spring.user.captcha;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import com.digitalsanctuary.spring.user.audit.AuditEvent;
import com.digitalsanctuary.spring.user.util.JSONResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Rejects POSTs to the framework's unauthenticated email-sending API actions unless they carry a
 * valid CAPTCHA token. Registered by {@link CaptchaAutoConfiguration} only when
 * {@code user.security.captcha.enabled=true}, against exactly the {@link CaptchaAction} paths.
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
 * same way as other API errors. Fail-closed: a missing token, an unavailable provider, a provider
 * error, a provider that throws, or an unrecognized path all reject the request before the handler
 * runs, so no email is sent.
 * </p>
 */
@Slf4j
@RequiredArgsConstructor
public class CaptchaValidationInterceptor implements HandlerInterceptor {

    /** Request header carrying the CAPTCHA response token (checked first). */
    public static final String TOKEN_HEADER = "X-Captcha-Token";

    /**
     * Request parameter carrying the CAPTCHA response token (fallback). Named for Cloudflare
     * Turnstile's own field so a Turnstile widget's default form field works unchanged; custom
     * {@link CaptchaService} providers receive the token through this same parameter regardless of
     * what their vendor calls it. Prefer {@value #TOKEN_HEADER}: query strings are recorded in
     * access logs, proxy logs, and {@code Referer} headers, and CAPTCHA tokens should not be.
     */
    public static final String TOKEN_PARAMETER = "cf-turnstile-response";

    /** JSONResponse code for CAPTCHA validation failures. */
    public static final int ERROR_CODE_CAPTCHA_FAILED = 8;

    private static final String MESSAGE_KEY = "message.captcha.validation-failed";
    private static final String DEFAULT_MESSAGE = "CAPTCHA verification failed. Please complete the challenge and try again.";

    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";
    private static final String UNKNOWN_FORWARDED_FOR = "unknown";

    /*
     * The action patterns compiled with the same PathPatternParser the InterceptorRegistry uses
     * (MappedInterceptor defaults to PathPatternParser.defaultInstance). Enforcement MUST use the
     * same pattern engine as registration: PathPattern matches against the parsed request path,
     * whose segments are URL-decoded and stripped of matrix parameters, so raw-URI string
     * comparison would let variants like "/user/registration;jsessionid=x" or
     * "/user/%72egistration" through unprotected while still reaching the handler.
     */
    private static final Map<CaptchaAction, PathPattern> ACTION_PATTERNS = buildActionPatterns();

    private static Map<CaptchaAction, PathPattern> buildActionPatterns() {
        Map<CaptchaAction, PathPattern> patterns = new EnumMap<>(CaptchaAction.class);
        for (CaptchaAction action : CaptchaAction.values()) {
            patterns.put(action, PathPatternParser.defaultInstance.parse(action.path()));
        }
        return patterns;
    }

    private final CaptchaConfigProperties captchaConfigProperties;
    private final ObjectProvider<CaptchaService> captchaServiceProvider;
    private final MessageSource messages;
    private final ObjectProvider<ObjectMapper> objectMapperProvider;
    private final ApplicationEventPublisher eventPublisher;

    /** Fallback used only when the application context has no ObjectMapper bean. */
    private static final ObjectMapper DEFAULT_OBJECT_MAPPER = JsonMapper.builder().build();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        String clientIp = resolveClientIp(request);
        CaptchaAction action = resolveAction(request);
        if (action == null) {
            // Unreachable through the real dispatcher: the interceptor is registered against
            // exactly the CaptchaAction paths, matched with this same engine. Reached only if a
            // request path cannot be parsed, or if registration and enforcement ever disagree.
            log.warn("CAPTCHA interceptor invoked for unrecognized path {} from {}. Rejecting (fail closed).", path,
                    clientIp);
            return reject(request, response, "Unrecognized protected path: " + path, clientIp);
        }
        if (!captchaConfigProperties.getProtect().isProtected(action)) {
            return true;
        }
        String token = resolveToken(request);
        if (token == null || token.isBlank()) {
            log.warn("CAPTCHA token missing on {} from {}. Rejecting request.", path, clientIp);
            return reject(request, response, "CAPTCHA token missing for action " + action, clientIp);
        }
        CaptchaService captchaService = captchaServiceProvider.getIfAvailable();
        if (captchaService == null) {
            log.error("CAPTCHA is enabled but no CaptchaService is available. Failing closed for {}.", path);
            return reject(request, response, "No CaptchaService available for action " + action, clientIp);
        }
        CaptchaVerification verification = verifySafely(captchaService, new CaptchaContext(action, token, clientIp, request));
        if (!verification.isVerified()) {
            log.warn("CAPTCHA {} on {} from {}. Rejecting request. Detail: {}", verification.outcome(), path, clientIp,
                    verification.detail());
            return reject(request, response,
                    "CAPTCHA " + verification.outcome() + " for action " + action + ": " + verification.detail(),
                    clientIp);
        }
        return true;
    }

    /**
     * Calls the provider and converts any failure into an {@link CaptchaVerification.Outcome#ERROR}
     * result. {@link CaptchaService} is a public SPI, so a third-party implementation may throw or
     * return null; the framework — not the implementation — enforces that such a failure rejects
     * the request and still produces the documented 403 body.
     */
    private CaptchaVerification verifySafely(CaptchaService captchaService, CaptchaContext context) {
        try {
            CaptchaVerification verification = captchaService.verify(context);
            if (verification == null) {
                log.error("CaptchaService {} returned null for action {}. Failing closed.",
                        captchaService.getClass().getName(), context.action());
                return CaptchaVerification.error("provider returned null");
            }
            return verification;
        } catch (RuntimeException e) {
            log.error("CaptchaService {} threw during verification of action {}. Failing closed.",
                    captchaService.getClass().getName(), context.action(), e);
            return CaptchaVerification.error("provider threw " + e.getClass().getSimpleName());
        }
    }

    /**
     * Returns the {@link CaptchaAction} this request targets, or null when the path cannot be
     * parsed or matches no known action. Matching uses the same {@code PathPattern} engine the
     * interceptor registration uses, so this decision cannot disagree with the registration's.
     */
    private CaptchaAction resolveAction(HttpServletRequest request) {
        PathContainer path;
        try {
            path = resolvePathWithinApplication(request);
        } catch (RuntimeException e) {
            log.warn("Could not parse request path {}. Failing closed.", request.getRequestURI(), e);
            return null;
        }
        for (Map.Entry<CaptchaAction, PathPattern> entry : ACTION_PATTERNS.entrySet()) {
            if (entry.getValue().matches(path)) {
                return entry.getKey();
            }
        }
        return null;
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

    /**
     * Resolves the client IP once so the value reported to the CAPTCHA provider and the value in
     * rejection logs are the same — otherwise the WARN stream names the proxy while the provider
     * sees the real client, and the logs cannot be used to identify an attacker.
     *
     * <p>
     * Uses the leftmost {@code X-Forwarded-For} entry when present, falling back to the socket
     * address. {@code X-Forwarded-For} is client-supplied and therefore only trustworthy when the
     * application sits behind a proxy that overwrites it; the value is advisory to the provider,
     * which scores the token itself.
     * </p>
     */
    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader(FORWARDED_FOR_HEADER);
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String first = forwardedFor.split(",", 2)[0].trim();
            // Some older proxies send the literal "unknown" rather than omitting the header; the
            // socket address is more useful than forwarding that placeholder to the provider.
            if (!first.isEmpty() && !UNKNOWN_FORWARDED_FOR.equalsIgnoreCase(first)) {
                return first;
            }
        }
        return request.getRemoteAddr();
    }

    private String resolveToken(HttpServletRequest request) {
        String token = request.getHeader(TOKEN_HEADER);
        if (token == null || token.isBlank()) {
            token = request.getParameter(TOKEN_PARAMETER);
        }
        return token;
    }

    private boolean reject(HttpServletRequest request, HttpServletResponse response, String reason, String clientIp)
            throws IOException {
        publishAuditEvent(request, reason, clientIp);
        String message = messages.getMessage(MESSAGE_KEY, null, DEFAULT_MESSAGE, request.getLocale());
        // Serialize the real JSONResponse rather than hand-building its shape, so a future field
        // added to JSONResponse cannot silently diverge this error from every other API error.
        JSONResponse body = JSONResponse.builder().success(false).code(ERROR_CODE_CAPTCHA_FAILED).message(message)
                .build();
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapperProvider.getIfAvailable(() -> DEFAULT_OBJECT_MAPPER)
                .writeValueAsString(body));
        return false;
    }

    /**
     * Records the rejection so operators can see CAPTCHA rejection volume the same way they see
     * every other rejection in the API, rather than only as WARN log lines.
     *
     * <p>
     * Uses {@code getSession(false)}: these requests are unauthenticated and frequently automated,
     * so creating a session for each one would let an abuser grow session storage just by being
     * rejected.
     * </p>
     */
    private void publishAuditEvent(HttpServletRequest request, String reason, String clientIp) {
        try {
            HttpSession session = request.getSession(false);
            eventPublisher.publishEvent(AuditEvent.builder().source(this).user(null)
                    .sessionId(session != null ? session.getId() : null).ipAddress(clientIp)
                    .userAgent(request.getHeader("User-Agent")).action("CaptchaValidation").actionStatus("Failure")
                    .message(reason).build());
        } catch (RuntimeException e) {
            // Auditing must never convert a rejection into a 500 — the request is being denied
            // either way, and that outcome matters more than the audit record.
            log.error("Failed to publish CAPTCHA rejection audit event.", e);
        }
    }
}
