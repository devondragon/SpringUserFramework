package com.digitalsanctuary.spring.user.security;

import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * An {@link AuthenticationEntryPoint} that detects HTMX requests and returns a JSON 401 response instead of the
 * default 302 redirect to the login page.
 *
 * <p>When an HTMX-powered page has polling or dynamic fragment requests and the user's session expires, Spring
 * Security's default entry point sends a 302 redirect to the login page. HTMX transparently follows the redirect and
 * swaps the full login page HTML into each target element, breaking the UI. This entry point intercepts HTMX requests
 * (identified by the {@code HX-Request} header) and returns a 401 status with a JSON body and an {@code HX-Redirect}
 * header, allowing the HTMX client to handle the session expiry gracefully.</p>
 *
 * <p>Non-HTMX requests are delegated to the wrapped {@link AuthenticationEntryPoint}, preserving the existing
 * redirect behavior for standard browser requests.</p>
 *
 * @see <a href="https://htmx.org/attributes/hx-request/">HTMX HX-Request Header</a>
 * @see <a href="https://htmx.org/headers/hx-redirect/">HTMX HX-Redirect Response Header</a>
 */
@Slf4j
public class HtmxAwareAuthenticationEntryPoint implements AuthenticationEntryPoint {

    static final String HX_REQUEST_HEADER = "HX-Request";
    static final String HX_REDIRECT_HEADER = "HX-Redirect";

    private final AuthenticationEntryPoint delegate;
    private final String loginUrl;

    /**
     * Creates a new HTMX-aware authentication entry point.
     *
     * @param delegate the entry point to delegate to for non-HTMX requests
     * @param loginUrl the login page URL used in the JSON response and HX-Redirect header
     */
    public HtmxAwareAuthenticationEntryPoint(AuthenticationEntryPoint delegate, String loginUrl) {
        this.delegate = delegate;
        this.loginUrl = loginUrl;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException, ServletException {
        if (isHtmxRequest(request)) {
            log.debug("HTMX request detected for URI {}; returning 401 with JSON body and HX-Redirect to {}",
                    request.getRequestURI(), loginUrl);

            if (response.isCommitted()) {
                log.warn("Response already committed for HTMX request to {}; cannot write 401 response",
                        request.getRequestURI());
                return;
            }

            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.setHeader(HX_REDIRECT_HEADER, loginUrl);
            String escapedLoginUrl = loginUrl.replace("\\", "\\\\").replace("\"", "\\\"");
            response.getWriter().write("{\"error\":\"authentication_required\","
                    + "\"message\":\"Session expired. Please log in.\","
                    + "\"loginUrl\":\"" + escapedLoginUrl + "\"}");
        } else {
            delegate.commence(request, response, authException);
        }
    }

    private boolean isHtmxRequest(HttpServletRequest request) {
        return "true".equalsIgnoreCase(request.getHeader(HX_REQUEST_HEADER));
    }
}
