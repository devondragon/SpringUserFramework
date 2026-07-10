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
    void stripsTrailingSlashFromConfiguredAppUrl() {
        // A consumer misconfiguring a trailing slash must not produce double slashes in email links
        // (e.g. appUrl + "/user/registrationConfirm?token=..." → "https://app.example.com//user/...")
        assertThat(new AppUrlResolver("https://app.example.com/", List.of())
                .resolveAppUrl(new MockHttpServletRequest())).isEqualTo("https://app.example.com");
        assertThat(new AppUrlResolver("https://app.example.com///", List.of())
                .resolveAppUrl(new MockHttpServletRequest())).isEqualTo("https://app.example.com");
    }

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
    void usesFirstTrustedHostWhenNonForwardedServerNameNotAllowListed() {
        // SUF-01 (CWE-640): with trustedHosts configured, the ordinary request server name (derived from the
        // Host header on common servlet containers) must ALSO be validated against the allow-list, not just
        // X-Forwarded-Host. An attacker-supplied Host must never flow into a reset/verification link; fall back
        // to the canonical first trusted host instead.
        AppUrlResolver resolver = new AppUrlResolver(null, List.of("app.example.com"));
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setScheme("https");
        req.setServerName("attacker.example");
        req.setServerPort(443);
        String resolved = resolver.resolveAppUrl(req);
        assertThat(resolved).isEqualTo("https://app.example.com");
        assertThat(resolved).doesNotContain("attacker.example");
    }

    @Test
    void usesNonForwardedServerNameWhenItIsAllowListed() {
        // Regression guard: a server name that IS in the allow-list is used as-is (no fallback).
        AppUrlResolver resolver = new AppUrlResolver(null, List.of("app.example.com"));
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setScheme("https");
        req.setServerName("app.example.com");
        req.setServerPort(443);
        assertThat(resolver.resolveAppUrl(req)).isEqualTo("https://app.example.com");
    }

    @Test
    void doesNotLeakUntrustedRequestPortWhenFallingBackToTrustedHost() {
        // The untrusted request's port (e.g. an internal 8443) must not leak into the canonical link.
        AppUrlResolver resolver = new AppUrlResolver(null, List.of("app.example.com"));
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setScheme("https");
        req.setServerName("attacker.example");
        req.setServerPort(8443);
        assertThat(resolver.resolveAppUrl(req)).isEqualTo("https://app.example.com");
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
    void matchesAllowListedForwardedHostCaseInsensitively() {
        // Hostnames are case-insensitive (RFC 4343): a mixed-case allow-list entry and a mixed-case
        // forwarded host must still match, otherwise the CWE-640 fix silently falls back to the server name.
        AppUrlResolver resolver = new AppUrlResolver(null, List.of("Trusted.Example.Com"));
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setScheme("http");
        req.setServerName("internal");
        req.setServerPort(8080);
        req.addHeader("X-Forwarded-Proto", "https");
        req.addHeader("X-Forwarded-Host", "TRUSTED.example.com");
        req.addHeader("X-Forwarded-Port", "443");
        assertThat(resolver.resolveAppUrl(req)).isEqualTo("https://TRUSTED.example.com");
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
    void matchesFirstValueOfMultiValuedForwardedHostAgainstAllowList() {
        AppUrlResolver resolver = new AppUrlResolver(null, List.of("trusted.example.com"));
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setScheme("http");
        req.setServerName("internal");
        req.setServerPort(8080);
        req.addHeader("X-Forwarded-Proto", "https");
        // Multi-proxy chain (RFC 7230): X-Forwarded-Host is a comma-separated list whose FIRST value is the
        // client-facing host. The allow-list must match that first value, not the whole compound string.
        req.addHeader("X-Forwarded-Host", "trusted.example.com, internal.proxy");
        req.addHeader("X-Forwarded-Port", "443");
        assertThat(resolver.resolveAppUrl(req)).isEqualTo("https://trusted.example.com");
    }

    @Test
    void ignoresInvalidForwardedProtoAndFallsBackToContainerScheme() {
        AppUrlResolver resolver = new AppUrlResolver(null, List.of("trusted.example.com"));
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setScheme("https");
        req.setServerName("internal");
        req.setServerPort(443);
        // A trusted but misconfigured/compromised proxy must never inject a non-http(s) scheme into a
        // security email link: an invalid X-Forwarded-Proto is ignored and the request scheme is used.
        req.addHeader("X-Forwarded-Proto", "javascript");
        req.addHeader("X-Forwarded-Host", "trusted.example.com");
        req.addHeader("X-Forwarded-Port", "443");
        String resolved = resolver.resolveAppUrl(req);
        assertThat(resolved).isEqualTo("https://trusted.example.com");
        assertThat(resolved).doesNotContain("javascript");
    }

    @Test
    void fallsBackToTrustedHostWhenNeitherForwardedNorServerNameIsAllowListed() {
        AppUrlResolver resolver = new AppUrlResolver(null, List.of("trusted.example.com"));
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setScheme("http");
        req.setServerName("internal");
        req.setServerPort(8080);
        req.addHeader("X-Forwarded-Proto", "https");
        req.addHeader("X-Forwarded-Host", "evil.com");
        req.addHeader("X-Forwarded-Port", "443");
        // SUF-01 (CWE-640): the untrusted forwarded host is ignored AND the ordinary server name ("internal") is
        // not allow-listed either, so neither may be emitted. Fall back to the canonical first trusted host with
        // the port reset to the scheme default. The internal host/port must not leak into the link.
        String resolved = resolver.resolveAppUrl(req);
        assertThat(resolved).isEqualTo("http://trusted.example.com");
        assertThat(resolved).doesNotContain("evil.com").doesNotContain("internal").doesNotContain("8080");
    }

    @Test
    void filtersBlankTrustedHostEntriesSoTheFallbackUsesTheRealHost() {
        // An empty/whitespace user.security.trustedHosts= property can bind as ["", "  ", "app.example.com"].
        // The blank entries must be filtered so the canonical fallback is the real host, not an empty authority
        // (which would produce "https://" with no host).
        AppUrlResolver resolver = new AppUrlResolver(null, List.of("", "   ", "app.example.com"));
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setScheme("https");
        req.setServerName("attacker.example");
        req.setServerPort(443);
        String resolved = resolver.resolveAppUrl(req);
        assertThat(resolved).isEqualTo("https://app.example.com");
        assertThat(resolved).doesNotContain("attacker.example");
    }

    @Test
    void treatsAnAllBlankTrustedHostsListAsUnconfiguredAndUsesTheServerName() {
        // If every trustedHosts entry is blank the allow-list is effectively empty, so the ordinary-host guard is
        // skipped and the request server name is used as-is (matching the empty-list behavior, not a fallback to "").
        AppUrlResolver resolver = new AppUrlResolver(null, List.of("", "   "));
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setScheme("https");
        req.setServerName("api.example.com");
        req.setServerPort(443);
        assertThat(resolver.resolveAppUrl(req)).isEqualTo("https://api.example.com");
    }

    @Test
    void matchesOrdinaryServerNameAgainstAllowListCaseInsensitively() {
        // Hostnames are case-insensitive (RFC 4343): a mixed-case allow-list entry must match a mixed-case ordinary
        // server name (Host-derived on common containers), not just X-Forwarded-Host, otherwise the SUF-01 guard
        // needlessly falls back to the canonical host for a legitimate request.
        AppUrlResolver resolver = new AppUrlResolver(null, List.of("App.Example.Com"));
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setScheme("https");
        req.setServerName("APP.example.COM");
        req.setServerPort(443);
        // Allow-listed case-insensitively, so the server name is used as-is (no fallback) with its original casing.
        assertThat(resolver.resolveAppUrl(req)).isEqualTo("https://APP.example.COM");
    }

    @Test
    void honorsAllowListedIpv6OrdinaryServerName() {
        // Documents the ordinary-host branch for IPv6 deployments: request.getServerName() already excludes the port
        // (unlike the forwarded-host path), so an allow-listed IPv6 literal is emitted correctly with no stripPort.
        AppUrlResolver resolver = new AppUrlResolver(null, List.of("[::1]"));
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setScheme("https");
        req.setServerName("[::1]");
        req.setServerPort(443);
        assertThat(resolver.resolveAppUrl(req)).isEqualTo("https://[::1]");
    }
}
