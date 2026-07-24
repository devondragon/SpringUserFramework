package com.digitalsanctuary.spring.user.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import com.digitalsanctuary.spring.user.test.annotations.SecurityTest;

/**
 * Authorization-model tests for {@link WebSecurityConfig} under {@code user.security.defaultAction=deny}.
 *
 * <p>
 * The {@code deny} model is the secure default: every request requires authentication <em>except</em> the explicitly
 * listed {@code unprotectedURIs} (plus the framework's own login/registration/forgot-password pages, which
 * {@code getUnprotectedURIsList()} always adds). These tests drive the <strong>real</strong> security filter chain via
 * {@code @SecurityTest}'s {@code MockMvc(addFilters = true)} so the authorization decisions are made by Spring Security
 * itself, not by mocks.
 * </p>
 *
 * <h2>How "rejected" vs "allowed" is asserted</h2>
 *
 * <p>
 * The test application defines no MVC handler for these paths, so a request that <em>passes</em> authorization falls
 * through to a 404 (no handler) rather than a 200. A request that is <em>rejected</em> by the anonymous-user
 * authentication entry point ({@link HtmxAwareAuthenticationEntryPoint} wrapping
 * {@code LoginUrlAuthenticationEntryPoint}) produces a 302 redirect to the login page for ordinary browser requests, or
 * a 401 for HTMX requests. So: 302/401 == rejected; 404 == authorization passed (the request reached the dispatcher).
 * This makes the authorization decision unambiguous without needing a stub controller.
 * </p>
 *
 * <p>
 * The URI lists come from {@code application-test.yml}: {@code unprotectedURIs} includes {@code /index.html};
 * {@code /protected.html} is NOT in that list, so under {@code deny} it requires authentication. {@code loginPageURI} is
 * {@code /user/login.html}.
 * </p>
 */
@SecurityTest
@TestPropertySource(properties = {"user.security.defaultAction=deny"})
@DisplayName("WebSecurityConfig Authorization - defaultAction=deny")
class WebSecurityAuthorizationDenyTest {

    /** Not present in unprotectedURIs, so it requires authentication under deny. */
    private static final String UNLISTED_URI = "/protected.html";

    /** Present in unprotectedURIs (application-test.yml), so it is anonymously accessible under deny. */
    private static final String UNPROTECTED_URI = "/index.html";

    private static final String LOGIN_PAGE = "/user/login.html";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("should redirect anonymous request for an unlisted URI to login when defaultAction is deny")
    void shouldRejectAnonymousUnlistedUriWhenDeny() throws Exception {
        mockMvc.perform(get(UNLISTED_URI))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(LOGIN_PAGE));
    }

    @Test
    @DisplayName("should return 401 for an HTMX anonymous request to an unlisted URI when defaultAction is deny")
    void shouldReturn401ForHtmxAnonymousUnlistedUriWhenDeny() throws Exception {
        mockMvc.perform(get(UNLISTED_URI).header("HX-Request", "true"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("HX-Redirect"));
    }

    @Test
    @DisplayName("should allow anonymous access to a listed unprotected URI when defaultAction is deny")
    void shouldAllowAnonymousUnprotectedUriWhenDeny() throws Exception {
        // 404 (no handler) proves the request passed authorization and reached the dispatcher; it must NOT be a
        // 302 redirect to login or a 401/403 rejection.
        mockMvc.perform(get(UNPROTECTED_URI))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("should allow an authenticated user to reach an otherwise-protected URI when defaultAction is deny")
    void shouldAllowAuthenticatedUserOnProtectedUriWhenDeny() throws Exception {
        // Authenticated => authorization passes => falls through to 404 (no handler). The key assertion is that it is
        // NOT redirected to login and NOT a 401/403. The user(...) post-processor attaches the authentication to the
        // request so it flows through the real security filter chain.
        mockMvc.perform(get(UNLISTED_URI).with(user("user@test.com").roles("USER")))
                .andExpect(status().isNotFound());
    }

    /**
     * The framework auto-unprotects always-public browser/crawler probe paths (apple-touch icons, favicons,
     * {@code /.well-known/**}) in {@code getUnprotectedURIsList()} so consumers do not have to list them and so the
     * probes do not 302 to login. These paths are intentionally absent from {@code application-test.yml}'s
     * {@code unprotectedURIs}, so a 404 (no handler, request reached the dispatcher) rather than a 302 proves the
     * auto-unprotect applied.
     */
    @org.junit.jupiter.api.Nested
    @DisplayName("auto-unprotected browser/crawler probe paths")
    class AutoUnprotectedProbePaths {

        @Test
        @DisplayName("should auto-unprotect /apple-touch-icon.png (Safari's default probe) without it being listed")
        void shouldAllowPlainAppleTouchIcon() throws Exception {
            mockMvc.perform(get("/apple-touch-icon.png")).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should auto-unprotect /apple-touch-icon-precomposed.png")
        void shouldAllowPrecomposedAppleTouchIcon() throws Exception {
            mockMvc.perform(get("/apple-touch-icon-precomposed.png")).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should auto-unprotect a sized apple-touch-icon variant (/apple-touch-icon-120x120.png)")
        void shouldAllowSizedAppleTouchIcon() throws Exception {
            mockMvc.perform(get("/apple-touch-icon-120x120.png")).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should auto-unprotect a favicon variant beyond the listed /favicon.ico (/favicon-32x32.png)")
        void shouldAllowFaviconVariant() throws Exception {
            mockMvc.perform(get("/favicon-32x32.png")).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should auto-unprotect /.well-known/** (e.g. security.txt)")
        void shouldAllowWellKnown() throws Exception {
            mockMvc.perform(get("/.well-known/security.txt")).andExpect(status().isNotFound());
        }
    }
}
