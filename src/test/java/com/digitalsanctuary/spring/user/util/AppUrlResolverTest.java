package com.digitalsanctuary.spring.user.util;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Unit tests for {@link AppUrlResolver}, verifying defense against Host-header / X-Forwarded-Host
 * poisoning (CWE-640).
 */
class AppUrlResolverTest {

    @Test
    void usesConfiguredAppUrlAndIgnoresForwardedHostWhenConfigured() {
        AppUrlResolver resolver = new AppUrlResolver("https://app.example.com", List.of());
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Forwarded-Host", "evil.com");
        assertThat(resolver.resolveAppUrl(req)).isEqualTo("https://app.example.com");
    }

    @Test
    void rejectsForwardedHostNotInAllowlistWhenNoConfiguredUrl() {
        AppUrlResolver resolver = new AppUrlResolver(null, List.of("trusted.example.com"));
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setScheme("https");
        req.setServerName("trusted.example.com");
        req.setServerPort(443);
        req.addHeader("X-Forwarded-Host", "evil.com");
        assertThat(resolver.resolveAppUrl(req)).contains("trusted.example.com").doesNotContain("evil.com");
    }

    @Test
    void honorsForwardedHostOnlyWhenAllowListed() {
        AppUrlResolver resolver = new AppUrlResolver(null, List.of("trusted.example.com"));
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setScheme("http");
        req.setServerName("internal");
        req.setServerPort(8080);
        req.addHeader("X-Forwarded-Proto", "https");
        req.addHeader("X-Forwarded-Host", "trusted.example.com");
        // A correctly-behaving HTTPS reverse proxy also forwards the public port; with the default
        // HTTPS port (443) the resolver omits the redundant ":port" suffix, yielding a clean URL.
        req.addHeader("X-Forwarded-Port", "443");
        assertThat(resolver.resolveAppUrl(req)).isEqualTo("https://trusted.example.com");
    }

    @Test
    void honorsAllowListedIpv6ForwardedHostWhenForwarded() {
        AppUrlResolver resolver = new AppUrlResolver(null, List.of("[::1]"));
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setScheme("http");
        req.setServerName("internal");
        req.setServerPort(8080);
        req.addHeader("X-Forwarded-Proto", "https");
        req.addHeader("X-Forwarded-Host", "[::1]:8443");
        // The IPv6 literal (with embedded colons) must be matched against the allow-list correctly,
        // so the trusted forwarded host is honored rather than falling back to the container host.
        String resolved = resolver.resolveAppUrl(req);
        assertThat(resolved).contains("[::1]").doesNotContain("internal");
    }

    @Test
    void derivesPortFromForwardedSchemeWhenForwardedPortAbsent() {
        AppUrlResolver resolver = new AppUrlResolver(null, List.of("trusted.example.com"));
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setScheme("http");
        req.setServerName("internal");
        req.setServerPort(8080);
        req.addHeader("X-Forwarded-Proto", "https");
        req.addHeader("X-Forwarded-Host", "trusted.example.com");
        // No X-Forwarded-Port: the port must be derived from the forwarded https scheme (443) and then
        // omitted as the default, NOT taken from the container's internal port (8080).
        assertThat(resolver.resolveAppUrl(req)).isEqualTo("https://trusted.example.com");
    }

    @Test
    void omitsDefaultPortAndContextPathForNonForwardedRequest() {
        AppUrlResolver resolver = new AppUrlResolver(null, List.of());
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setScheme("https");
        req.setServerName("api.example.com");
        req.setServerPort(443);
        req.setContextPath("");
        assertThat(resolver.resolveAppUrl(req)).isEqualTo("https://api.example.com");
    }

    @Test
    void includesNonDefaultPortAndContextPathForNonForwardedRequest() {
        AppUrlResolver resolver = new AppUrlResolver(null, List.of());
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setScheme("http");
        req.setServerName("localhost");
        req.setServerPort(8080);
        req.setContextPath("/app");
        assertThat(resolver.resolveAppUrl(req)).isEqualTo("http://localhost:8080/app");
    }

    @Test
    void ignoresUntrustedForwardedHostAndUsesContainerServerName() {
        AppUrlResolver resolver = new AppUrlResolver(null, List.of("trusted.example.com"));
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setScheme("http");
        req.setServerName("internal");
        req.setServerPort(8080);
        req.addHeader("X-Forwarded-Proto", "https");
        req.addHeader("X-Forwarded-Host", "evil.com");
        req.addHeader("X-Forwarded-Port", "443");
        // Untrusted forwarded host -> forwarded headers are NOT honored; the container's own
        // scheme/host/port are used instead.
        String resolved = resolver.resolveAppUrl(req);
        assertThat(resolved).isEqualTo("http://internal:8080");
        assertThat(resolved).doesNotContain("evil.com");
    }
}
