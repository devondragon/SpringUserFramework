package com.digitalsanctuary.spring.user.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import com.digitalsanctuary.spring.user.test.annotations.SecurityTest;
import jakarta.servlet.Filter;

/**
 * Integration test proving that multi-factor login is functional: factor authorities from successive login steps must
 * MERGE into a single {@link Authentication} so a user who completes every required factor can actually reach protected
 * resources.
 * <p>
 * Spring Security 7's factor merging is performed inside {@code AbstractAuthenticationProcessingFilter.doFilter} ONLY
 * when {@code mfaEnabled} has been set to {@code true} on the authentication processing filters. That flag is normally
 * flipped by {@code @EnableMultiFactorAuthentication}'s {@code EnableMfaFiltersPostProcessor}. Before the H4 fix, this
 * framework configured the enforcement side ({@code AllRequiredFactorsAuthorizationManager}) but never activated the
 * merging filters, so completing a second factor REPLACED the authentication (losing the first factor) and the user was
 * permanently locked out.
 * </p>
 * <p>
 * The two tests cover different halves of the fix:
 * </p>
 * <ul>
 * <li>{@link #authenticationProcessingFiltersHaveMfaMergingEnabled()} is what proves the <b>merging</b> side. It
 * reflectively asserts {@code mfaEnabled == true} on the authentication processing filters; before the fix the
 * form-login and WebAuthn filters have {@code mfaEnabled == false}, so cross-step factor merging never happens.</li>
 * <li>{@link #bothFactorsGrantAccessWhileSingleFactorIsDenied()} injects a single, already PRE-MERGED authentication
 * (both factor authorities in one token) rather than completing two login steps. It therefore exercises the
 * <b>enforcement</b> side &mdash; that {@code .authenticated()} requires all configured factors and that an
 * authentication carrying every factor is granted &mdash; and acts as a regression guard against introducing a second
 * {@code AuthorizationManagerFactory} bean that would make Spring Security's by-type lookup ambiguous and silently
 * disable factor enforcement. It does NOT exercise the cross-step merging itself; that is the reflective test's job.</li>
 * </ul>
 */
@SecurityTest
@TestPropertySource(properties = {"user.mfa.enabled=true", "user.mfa.factors=PASSWORD,WEBAUTHN", "user.webauthn.enabled=true"})
@DisplayName("MFA Login Integration Tests (H4)")
class MfaLoginIntegrationTest {

    /** A request path that requires authentication under the test profile's {@code defaultAction=deny}. */
    private static final String PROTECTED_URI = "/protected.html";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FilterChainProxy filterChainProxy;

    @Test
    @DisplayName("authentication processing filters have MFA factor-merging enabled when MFA is configured")
    void authenticationProcessingFiltersHaveMfaMergingEnabled() {
        List<AbstractAuthenticationProcessingFilter> processingFilters = findAuthenticationProcessingFilters();

        assertThat(processingFilters)
                .as("the security filter chain must contain authentication processing filters (e.g. form login, WebAuthn)")
                .isNotEmpty();

        // The actual H4 gap: every authentication processing filter must have mfaEnabled=true, otherwise completing a
        // second factor replaces the existing authentication instead of merging factor authorities onto it.
        assertThat(processingFilters)
                .as("every authentication processing filter must have mfaEnabled=true so factor authorities merge across login steps")
                .allSatisfy(filter -> assertThat((Boolean) ReflectionTestUtils.getField(filter, "mfaEnabled"))
                        .as("mfaEnabled on %s", filter.getClass().getSimpleName())
                        .isTrue());
    }

    @Test
    @DisplayName("both factor authorities grant access while a single factor is denied")
    void bothFactorsGrantAccessWhileSingleFactorIsDenied() throws Exception {
        // Step 1: only the PASSWORD factor present -> the WEBAUTHN factor is still required, so access is denied.
        Authentication passwordOnly = authenticationWithFactors(FactorGrantedAuthority.PASSWORD_AUTHORITY);
        mockMvc.perform(get(PROTECTED_URI).with(authentication(passwordOnly)))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .as("password-only authentication must NOT be granted access to a protected resource")
                        .isNotEqualTo(200));

        // Step 2: both factor authorities present in ONE authentication -> all required factors satisfied -> access granted.
        Authentication bothFactors = authenticationWithFactors(FactorGrantedAuthority.PASSWORD_AUTHORITY,
                FactorGrantedAuthority.WEBAUTHN_AUTHORITY);
        mockMvc.perform(get(PROTECTED_URI).with(authentication(bothFactors)))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .as("authentication carrying BOTH factor authorities must be granted access (no 401/403/redirect)")
                        .isNotIn(401, 403, 302));
    }

    /**
     * Builds an authenticated token for {@code user@test.com} carrying {@code ROLE_USER} plus the supplied factor
     * authorities.
     *
     * @param factorAuthorities the {@link FactorGrantedAuthority} authority strings to attach
     * @return an authenticated {@link Authentication}
     */
    private Authentication authenticationWithFactors(String... factorAuthorities) {
        List<GrantedAuthority> authorities = new java.util.ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        for (String factor : factorAuthorities) {
            authorities.add(FactorGrantedAuthority.fromAuthority(factor));
        }
        return UsernamePasswordAuthenticationToken.authenticated("user@test.com", null, authorities);
    }

    /**
     * Walks every {@link SecurityFilterChain} and collects the {@link AbstractAuthenticationProcessingFilter} instances
     * (form login, WebAuthn, etc.) that participate in MFA factor merging.
     *
     * @return the authentication processing filters present in the configured filter chains
     */
    private List<AbstractAuthenticationProcessingFilter> findAuthenticationProcessingFilters() {
        List<AbstractAuthenticationProcessingFilter> result = new java.util.ArrayList<>();
        for (SecurityFilterChain chain : filterChainProxy.getFilterChains()) {
            for (Filter filter : chain.getFilters()) {
                if (filter instanceof AbstractAuthenticationProcessingFilter processingFilter) {
                    result.add(processingFilter);
                }
            }
        }
        return result;
    }
}
