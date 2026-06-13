package com.digitalsanctuary.spring.user.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import com.digitalsanctuary.spring.user.test.annotations.SecurityTest;

/**
 * Authorization-model tests for {@link WebSecurityConfig} under {@code user.security.defaultAction=allow}.
 *
 * <p>
 * The {@code allow} model inverts the default: everything is anonymously accessible <em>except</em> the explicitly
 * listed {@code protectedURIs}. These tests drive the real security filter chain via {@code @SecurityTest}'s
 * {@code MockMvc(addFilters = true)}.
 * </p>
 *
 * <p>
 * As in the deny tests, the test app has no MVC handler for these paths, so a request that <em>passes</em> authorization
 * returns 404 (reached the dispatcher, no handler), while a <em>rejected</em> anonymous request to a protected URI
 * returns a 302 redirect to the login page. {@code protectedURIs} is {@code /protected.html} (from
 * {@code application-test.yml}); any other path is unprotected under {@code allow}.
 * </p>
 */
@SecurityTest
@TestPropertySource(properties = {"user.security.defaultAction=allow", "user.security.protectedURIs=/protected.html"})
@DisplayName("WebSecurityConfig Authorization - defaultAction=allow")
class WebSecurityAuthorizationAllowTest {

    /** Listed in protectedURIs, so it requires authentication even under allow. */
    private static final String PROTECTED_URI = "/protected.html";

    /** Not listed in protectedURIs, so it is anonymously accessible under allow. */
    private static final String UNLISTED_URI = "/some/random/unlisted/path";

    private static final String LOGIN_PAGE = "/user/login.html";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("should redirect anonymous request for a protected URI to login when defaultAction is allow")
    void shouldRejectAnonymousProtectedUriWhenAllow() throws Exception {
        mockMvc.perform(get(PROTECTED_URI))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(LOGIN_PAGE));
    }

    @Test
    @DisplayName("should allow anonymous access to an unlisted URI when defaultAction is allow")
    void shouldAllowAnonymousUnlistedUriWhenAllow() throws Exception {
        // 404 (no handler) proves the request passed authorization; it must NOT redirect to login.
        mockMvc.perform(get(UNLISTED_URI))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("should allow an authenticated user to reach a protected URI when defaultAction is allow")
    void shouldAllowAuthenticatedUserOnProtectedUriWhenAllow() throws Exception {
        mockMvc.perform(get(PROTECTED_URI).with(user("user@test.com").roles("USER")))
                .andExpect(status().isNotFound());
    }
}
