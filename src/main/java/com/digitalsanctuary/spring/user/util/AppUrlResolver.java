package com.digitalsanctuary.spring.user.util;

import java.util.List;
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
 * <li>Otherwise build from the request, honoring {@code X-Forwarded-*} only when the resolved host is in {@code user.security.trustedHosts};
 * otherwise use the container's own server name (the untrusted forwarded host is ignored and a warning is logged).</li>
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
        this.configuredAppUrl = (configuredAppUrl == null || configuredAppUrl.isBlank()) ? null : configuredAppUrl.trim();
        this.trustedHosts = trustedHosts == null ? List.of() : trustedHosts.stream().map(String::trim).toList();
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

        String fwdHost = request.getHeader("X-Forwarded-Host");
        boolean useForwarded = fwdHost != null && !fwdHost.isEmpty() && trustedHosts.contains(stripPort(fwdHost));
        if (fwdHost != null && !fwdHost.isEmpty() && !useForwarded) {
            log.warn("AppUrlResolver: ignoring untrusted X-Forwarded-Host '{}' (not in user.security.trustedHosts)", fwdHost);
        }

        String scheme = useForwarded ? headerOr(request, "X-Forwarded-Proto", request.getScheme()) : request.getScheme();
        String host = useForwarded ? stripPort(fwdHost) : request.getServerName();
        int port = useForwarded ? forwardedPort(request, scheme) : request.getServerPort();

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
        String portHeader = request.getHeader("X-Forwarded-Port");
        if (portHeader != null && !portHeader.isBlank()) {
            try {
                return Integer.parseInt(portHeader.trim());
            } catch (NumberFormatException e) {
                log.warn("AppUrlResolver: ignoring non-numeric X-Forwarded-Port '{}'", portHeader);
            }
        }
        // No usable X-Forwarded-Port: derive the port from the forwarded scheme so we don't leak the
        // container's internal port (e.g. 8080) into the email link. Default ports are omitted later.
        return "https".equalsIgnoreCase(forwardedScheme) ? DEFAULT_HTTPS_PORT : DEFAULT_HTTP_PORT;
    }

    private static boolean isDefaultPort(String scheme, int port) {
        return ("http".equalsIgnoreCase(scheme) && port == DEFAULT_HTTP_PORT)
                || ("https".equalsIgnoreCase(scheme) && port == DEFAULT_HTTPS_PORT);
    }

    private static String headerOr(HttpServletRequest req, String header, String fallback) {
        String v = req.getHeader(header);
        return (v == null || v.isEmpty()) ? fallback : v;
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
