package com.digitalsanctuary.spring.user.util;

import java.util.List;
import java.util.Locale;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolves the application base URL used to build security-sensitive email links (password reset, email verification), defending against Host-header /
 * X-Forwarded-Host poisoning (CWE-640).
 *
 * <p>
 * Resolution order:
 * <ol>
 * <li>If {@code user.security.appUrl} is configured, always use it (forwarded headers ignored).</li>
 * <li>Otherwise build from the request. {@code X-Forwarded-*} is honored only when the forwarded host is in {@code user.security.trustedHosts}. When
 * {@code trustedHosts} is configured, the ordinary request server name must also be in it &mdash; a non-allow-listed host (e.g. a spoofed {@code Host}
 * header) falls back to the first configured trusted host rather than being emitted into the link. When {@code trustedHosts} is empty, the request
 * server name is used as-is; configure {@code user.security.appUrl} or {@code trustedHosts} to prevent Host-header link poisoning.</li>
 * </ol>
 *
 * <p>
 * The request-derived URL preserves the historical {@code UserUtils.getAppUrl} format with one refinement: the {@code :port} suffix is omitted when the
 * port is the default for the scheme (80 for {@code http}, 443 for {@code https}), and the context path is appended only when non-empty. This keeps
 * link formatting stable for existing deployments while producing clean canonical URLs.
 */
@Slf4j
public class AppUrlResolver {

    private static final int DEFAULT_HTTP_PORT = 80;
    private static final int DEFAULT_HTTPS_PORT = 443;

    private final String configuredAppUrl;
    private final List<String> trustedHosts;

    /**
     * Creates a resolver.
     *
     * @param configuredAppUrl the canonical base URL ({@code user.security.appUrl}); blank/null means "not configured"
     * @param trustedHosts the allow-listed forwarded hosts ({@code user.security.trustedHosts}); null is treated as empty
     */
    public AppUrlResolver(String configuredAppUrl, List<String> trustedHosts) {
        String trimmed = (configuredAppUrl == null || configuredAppUrl.isBlank()) ? null : configuredAppUrl.trim();
        // Strip any trailing slash to honour the "no trailing slash" contract on resolveAppUrl's return value.
        // Without this, appUrl + "/user/..." produces double slashes when the consumer misconfigures a trailing slash.
        this.configuredAppUrl = (trimmed != null && trimmed.endsWith("/")) ? trimmed.replaceAll("/+$", "") : trimmed;
        // Hostnames are case-insensitive (RFC 4343); normalise the allow-list to lower case so a mixed-case
        // configured or forwarded host (e.g. "App.Example.Com") still matches "app.example.com".
        this.trustedHosts = trustedHosts == null ? List.of()
                : trustedHosts.stream().map(s -> s.trim().toLowerCase(Locale.ROOT)).filter(s -> !s.isBlank()).toList();
    }

    /**
     * Resolves the application base URL for the given request.
     *
     * @param request the current HTTP request
     * @return the resolved base URL (no trailing slash)
     */
    public String resolveAppUrl(HttpServletRequest request) {
        if (configuredAppUrl != null) {
            return configuredAppUrl;
        }

        // X-Forwarded-Host may be a comma-separated list when the request traverses multiple proxies
        // (RFC 7230); the client-facing host is the first value. Match the allow-list against that value
        // only, otherwise legitimate multi-proxy chains (e.g. ALB + nginx) never match and silently fall
        // back to the container's server name.
        String fwdHost = firstHeaderValue(request.getHeader("X-Forwarded-Host"));
        boolean useForwarded = fwdHost != null && !fwdHost.isEmpty() && trustedHosts.contains(stripPort(fwdHost).toLowerCase(Locale.ROOT));
        if (fwdHost != null && !fwdHost.isEmpty() && !useForwarded) {
            log.warn("AppUrlResolver: ignoring untrusted X-Forwarded-Host '{}' (not in user.security.trustedHosts)", sanitizeForLog(fwdHost));
        }

        String scheme = useForwarded ? forwardedScheme(request) : request.getScheme();
        String host = useForwarded ? stripPort(fwdHost) : request.getServerName();
        int port = useForwarded ? forwardedPort(request, scheme) : request.getServerPort();

        // SUF-01 (CWE-640): when a trusted-host allow-list is configured, the finally-chosen host must be in it —
        // including the ordinary request server name, which on common servlet containers is derived from the Host
        // header and is therefore attacker-influenced. A trusted X-Forwarded-Host already satisfies this (it is only
        // used when allow-listed), so this guard effectively validates the non-forwarded server name. If the host is
        // not allow-listed, fall back to the first configured trusted host (a known-good canonical authority) rather
        // than emitting the untrusted value, and reset the port to the scheme default so the untrusted request's port
        // cannot leak. When trustedHosts is empty this block is skipped and the server name is used as-is (see the
        // startup warning in UserSecurityBeansAutoConfiguration).
        if (!trustedHosts.isEmpty() && (host == null || !trustedHosts.contains(host.toLowerCase(Locale.ROOT)))) {
            String canonical = trustedHosts.get(0);
            log.warn("AppUrlResolver: request host '{}' is not in user.security.trustedHosts; using canonical trusted host '{}' for the email link",
                    sanitizeForLog(host), canonical);
            host = canonical;
            port = "https".equalsIgnoreCase(scheme) ? DEFAULT_HTTPS_PORT : DEFAULT_HTTP_PORT;
        }

        StringBuilder url = new StringBuilder();
        url.append(scheme).append("://").append(host);
        if (!isDefaultPort(scheme, port)) {
            url.append(':').append(port);
        }
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty()) {
            url.append(contextPath);
        }
        return url.toString();
    }

    private static int forwardedPort(HttpServletRequest request, String forwardedScheme) {
        String portHeader = firstHeaderValue(request.getHeader("X-Forwarded-Port"));
        if (portHeader != null && !portHeader.isBlank()) {
            try {
                return Integer.parseInt(portHeader.trim());
            } catch (NumberFormatException e) {
                log.warn("AppUrlResolver: ignoring non-numeric X-Forwarded-Port '{}'", sanitizeForLog(portHeader));
            }
        }
        // No usable X-Forwarded-Port: derive the port from the forwarded scheme so we don't leak the
        // container's internal port (e.g. 8080) into the email link. Default ports are omitted later.
        return "https".equalsIgnoreCase(forwardedScheme) ? DEFAULT_HTTPS_PORT : DEFAULT_HTTP_PORT;
    }

    /**
     * Resolves the forwarded scheme from {@code X-Forwarded-Proto}, accepting only {@code http} or {@code https}. A trusted proxy is expected to send a
     * sane value, but a misconfigured or compromised one sending e.g. {@code javascript} must never be allowed to flow into a security-sensitive email
     * link, so any unrecognized value falls back to the container's own scheme.
     */
    private static String forwardedScheme(HttpServletRequest request) {
        String proto = firstHeaderValue(request.getHeader("X-Forwarded-Proto"));
        if (proto == null || proto.isEmpty()) {
            return request.getScheme();
        }
        if ("http".equalsIgnoreCase(proto) || "https".equalsIgnoreCase(proto)) {
            return proto;
        }
        log.warn("AppUrlResolver: ignoring invalid X-Forwarded-Proto '{}', falling back to request scheme", sanitizeForLog(proto));
        return request.getScheme();
    }

    /**
     * Returns the first value of a possibly comma-separated forwarded header (RFC 7230), trimmed. Returns {@code null} for a null input.
     */
    private static String firstHeaderValue(String headerValue) {
        if (headerValue == null) {
            return null;
        }
        int comma = headerValue.indexOf(',');
        String first = comma >= 0 ? headerValue.substring(0, comma) : headerValue;
        return first.trim();
    }

    /**
     * Neutralizes CR/LF/tab in attacker-controlled header values before logging to prevent log-injection / forging.
     */
    private static String sanitizeForLog(String value) {
        return value == null ? null : value.replaceAll("[\r\n\t]", "_");
    }

    private static boolean isDefaultPort(String scheme, int port) {
        return ("http".equalsIgnoreCase(scheme) && port == DEFAULT_HTTP_PORT)
                || ("https".equalsIgnoreCase(scheme) && port == DEFAULT_HTTPS_PORT);
    }

    private static String stripPort(String host) {
        if (host.startsWith("[")) {
            // IPv6 literal: [::1] or [::1]:8443
            int bracket = host.indexOf(']');
            return bracket > 0 ? host.substring(0, bracket + 1) : host;
        }
        int colon = host.lastIndexOf(':');
        return colon > 0 ? host.substring(0, colon) : host;
    }
}
