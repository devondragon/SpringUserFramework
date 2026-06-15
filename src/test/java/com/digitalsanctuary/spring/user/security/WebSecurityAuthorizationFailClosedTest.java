package com.digitalsanctuary.spring.user.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import com.digitalsanctuary.spring.user.test.annotations.SecurityTest;

/**
 * Fail-closed test for {@link WebSecurityConfig} when {@code user.security.defaultAction} is set to an
 * unrecognized/typo'd value.
 *
 * <p>
 * {@code buildSecurityFilterChain} only treats the literal strings {@code "deny"} and {@code "allow"} as valid. Any
 * other value (a typo, an empty/garbage string) falls into the {@code else} branch, which logs an error and configures
 * {@code anyRequest().denyAll()}. This is the secure, <strong>fail-closed</strong> behavior: a misconfiguration denies
 * everything rather than silently allowing access. These tests lock that behavior in.
 * </p>
 *
 * <p>
 * The strongest assertion is that even an <em>authenticated</em> user is denied (403) for a path that, under any valid
 * configuration, they would be allowed to reach (it would fall through to a 404 no-handler). Under {@code denyAll()},
 * authentication is irrelevant — access is refused outright. For an anonymous user, {@code denyAll()} raises an
 * {@code AuthenticationException}, so the authentication entry point redirects to the login page (a 3xx) rather than
 * serving the resource. Either way, the resource is never served — confirming fail-closed.
 * </p>
 */
@SecurityTest
@TestPropertySource(properties = {"user.security.defaultAction=bogus-typo-value"})
@DisplayName("WebSecurityConfig Authorization - unrecognized defaultAction fails closed")
class WebSecurityAuthorizationFailClosedTest {

    /** A path that is in unprotectedURIs under the test profile; under a VALID config it would be reachable. */
    private static final String NORMALLY_ALLOWED_URI = "/index.html";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("should deny an authenticated user even on a normally-allowed URI when defaultAction is unrecognized")
    void shouldDenyAuthenticatedUserWhenDefaultActionIsUnrecognized() throws Exception {
        // Under deny/allow this same authenticated request would pass authorization and fall through to a 404.
        // Fail-closed denyAll() refuses it with 403 regardless of authentication.
        mockMvc.perform(get(NORMALLY_ALLOWED_URI).with(user("user@test.com").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should not serve the resource to an anonymous user when defaultAction is unrecognized")
    void shouldNotServeAnonymousRequestWhenDefaultActionIsUnrecognized() throws Exception {
        // Anonymous + denyAll() => AuthenticationException => entry point redirects to login (3xx). The resource is
        // never served (no 2xx, no 404 fall-through).
        mockMvc.perform(get(NORMALLY_ALLOWED_URI))
                .andExpect(status().is3xxRedirection());
    }
}
