package com.digitalsanctuary.spring.user.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import com.digitalsanctuary.spring.user.test.annotations.SecurityTest;

/**
 * CSRF-enforcement tests for {@link WebSecurityConfig}.
 *
 * <p>
 * Spring Security enables CSRF protection by default; {@link WebSecurityConfig} additionally exempts the paths listed in
 * {@code user.security.disableCSRFURIs} via {@code csrf().ignoringRequestMatchers(...)}. Under the test profile
 * ({@code application-test.yml}) {@code disableCSRFURIs=/no-csrf-test}. These tests drive the real filter chain via
 * {@code @SecurityTest}'s {@code MockMvc(addFilters = true)}.
 * </p>
 *
 * <h2>Isolating CSRF from authorization</h2>
 *
 * <p>
 * A CSRF rejection is a 403 produced by the {@code CsrfFilter}, which runs <em>before</em> authorization. To make CSRF
 * the only gate under test, the authenticated cases use {@code @WithMockUser} so the authorization layer would otherwise
 * let the request through (the test app has no handler for these paths, so a fully-passing request yields a 404). Thus:
 * a 403 here means CSRF rejected the request; a 404 means it passed CSRF (and authorization) and reached the dispatcher.
 * </p>
 */
@SecurityTest
@TestPropertySource(properties = {"user.security.defaultAction=deny", "user.security.disableCSRFURIs=/no-csrf-test"})
@DisplayName("WebSecurityConfig CSRF Enforcement")
class WebSecurityCsrfTest {

    /** A CSRF-protected path (not in disableCSRFURIs). */
    private static final String CSRF_PROTECTED_URI = "/protected.html";

    /** A path listed in disableCSRFURIs, so the CsrfFilter ignores it. */
    private static final String CSRF_EXEMPT_URI = "/no-csrf-test";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("should reject a POST without a CSRF token with 403 on a CSRF-protected endpoint")
    void shouldRejectPostWithoutCsrfTokenOnProtectedEndpoint() throws Exception {
        // Authenticated, so authorization would pass; the only thing that can reject this is the missing CSRF token.
        mockMvc.perform(post(CSRF_PROTECTED_URI).with(user("user@test.com").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should pass the CSRF filter when a POST carries a valid CSRF token")
    void shouldPassCsrfFilterWhenValidTokenProvided() throws Exception {
        // With a valid token the request clears CSRF; authorization also passes (authenticated), so it falls through
        // to a 404 (no handler). The load-bearing assertion is that it is NOT a 403 CSRF rejection.
        mockMvc.perform(post(CSRF_PROTECTED_URI).with(user("user@test.com").roles("USER")).with(csrf()))
                .andExpect(status().is(Matchers.not(403)));
    }

    @Test
    @DisplayName("should not reject a POST without a CSRF token on a disableCSRFURIs path")
    void shouldNotEnforceCsrfOnExemptPath() throws Exception {
        // The path is in disableCSRFURIs, so the CsrfFilter ignores it; with an authenticated user the request passes
        // authorization too and falls through to a 404. The key assertion: NOT a 403 CSRF rejection despite no token.
        mockMvc.perform(post(CSRF_EXEMPT_URI).with(user("user@test.com").roles("USER")))
                .andExpect(status().is(Matchers.not(403)))
                .andExpect(status().isNotFound());
    }
}
