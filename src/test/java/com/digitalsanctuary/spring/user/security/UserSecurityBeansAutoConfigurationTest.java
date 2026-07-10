package com.digitalsanctuary.spring.user.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.userdetails.UserDetailsService;
import com.digitalsanctuary.spring.user.roles.RolesAndPrivilegesConfig;
import com.digitalsanctuary.spring.user.util.AppUrlResolver;

/**
 * Unit tests for {@link UserSecurityBeansAutoConfiguration#appUrlResolver}, focused on the SUF-01 (CWE-640)
 * opt-in strict mode ({@code user.security.requireCanonicalAppUrl}).
 */
class UserSecurityBeansAutoConfigurationTest {

    private final UserSecurityBeansAutoConfiguration config =
            new UserSecurityBeansAutoConfiguration(mock(UserDetailsService.class), mock(RolesAndPrivilegesConfig.class));

    @Test
    @DisplayName("strict mode fails startup when neither appUrl nor trustedHosts is configured")
    void strictMode_failsStartupWhenNothingConfigured() {
        assertThatThrownBy(() -> config.appUrlResolver(null, List.of(), true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("requireCanonicalAppUrl");
    }

    @Test
    @DisplayName("strict mode allows startup when a canonical appUrl is configured, and the resolver uses it")
    void strictMode_allowsStartupWhenAppUrlConfigured() {
        AppUrlResolver resolver = config.appUrlResolver("https://app.example.com", List.of(), true);
        assertThat(resolver).isNotNull();
        // Prove the configured appUrl actually flows into the resolver, not just that a bean was returned.
        assertThat(resolver.resolveAppUrl(new MockHttpServletRequest())).isEqualTo("https://app.example.com");
    }

    @Test
    @DisplayName("strict mode allows startup when a trusted-host allow-list is configured, and the resolver uses it")
    void strictMode_allowsStartupWhenTrustedHostsConfigured() {
        AppUrlResolver resolver = config.appUrlResolver(null, List.of("app.example.com"), true);
        assertThat(resolver).isNotNull();
        // Prove the configured allow-list actually flows into the resolver: a non-allow-listed request host must
        // fall back to the canonical trusted host rather than being emitted.
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setScheme("https");
        req.setServerName("attacker.example");
        req.setServerPort(443);
        assertThat(resolver.resolveAppUrl(req)).isEqualTo("https://app.example.com");
    }

    @Test
    @DisplayName("strict mode treats a blank-only trustedHosts value as unconfigured and fails startup")
    void strictMode_failsStartupWhenTrustedHostsBlankOnly() {
        // An empty user.security.trustedHosts= property can bind as [""]; that is not a real allow-list.
        assertThatThrownBy(() -> config.appUrlResolver(null, List.of(""), true))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("non-strict mode returns a resolver even when nothing is configured (warns, does not fail)")
    void nonStrictMode_returnsResolverWhenNothingConfigured() {
        assertThat(config.appUrlResolver(null, List.of(), false)).isNotNull();
    }
}
