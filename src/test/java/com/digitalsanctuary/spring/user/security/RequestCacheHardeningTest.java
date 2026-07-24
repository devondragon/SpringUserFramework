package com.digitalsanctuary.spring.user.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import com.digitalsanctuary.spring.user.test.annotations.SecurityTest;

/**
 * Tests for the hardened {@link org.springframework.security.web.savedrequest.RequestCache} contributed by
 * {@link UserSecurityBeansAutoConfiguration} and wired into the filter chain by {@link WebSecurityConfig}.
 *
 * <p>
 * Background: browsers (Safari/iOS in particular) automatically probe URLs like {@code /apple-touch-icon.png} and
 * {@code /apple-touch-icon-precomposed.png} at the site root while rendering any page &mdash; including the login page.
 * With Spring Security's default request cache, such a probe against a <em>protected</em> URL overwrites the saved
 * request created by the user's real navigation (e.g. a deep link from an email), so the post-login redirect sends the
 * user to {@code /apple-touch-icon.png?continue} instead of their original destination.
 * </p>
 *
 * <p>
 * The hardened cache only saves requests that are plausibly a user navigation: {@code GET} requests that explicitly
 * accept {@code text/html}, are not XHR ({@code X-Requested-With}) or HTMX ({@code HX-Request}) calls, and do not
 * target well-known auto-probed/static asset paths (favicons, touch icons, manifests, common static file extensions).
 * </p>
 */
@SecurityTest
@TestPropertySource(properties = {"user.security.defaultAction=deny"})
@DisplayName("Hardened RequestCache - saved request survives browser icon probes")
class RequestCacheHardeningTest {

    /** Session attribute where HttpSessionRequestCache stores the saved request. */
    private static final String SAVED_REQUEST_ATTR = "SPRING_SECURITY_SAVED_REQUEST";

    /** Not present in unprotectedURIs, so it requires authentication under deny. */
    private static final String PROTECTED_PAGE = "/protected.html";

    /** Auto-probed by Safari/iOS; intentionally NOT in the test unprotectedURIs so it is a protected URL. */
    private static final String TOUCH_ICON = "/apple-touch-icon.png";

    /** Typical browser navigation Accept header. */
    private static final String BROWSER_ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("should save a browser HTML navigation to a protected page")
    void shouldSaveBrowserNavigation() throws Exception {
        MvcResult result = mockMvc.perform(get(PROTECTED_PAGE).header("Accept", BROWSER_ACCEPT)).andReturn();

        SavedRequest saved = savedRequest(result);
        assertThat(saved).as("real navigation should be saved for post-login redirect").isNotNull();
        assertThat(saved.getRedirectUrl()).contains(PROTECTED_PAGE);
    }

    @Test
    @DisplayName("should NOT let an icon probe overwrite the saved navigation (the /apple-touch-icon.png?continue bug)")
    void iconProbeMustNotOverwriteSavedRequest() throws Exception {
        // 1. User clicks a deep link to a protected page -> redirected to login, request saved.
        MvcResult navigation = mockMvc.perform(get(PROTECTED_PAGE).header("Accept", BROWSER_ACCEPT)).andReturn();
        MockHttpSession session = (MockHttpSession) navigation.getRequest().getSession(false);
        assertThat(savedRequest(navigation)).isNotNull();

        // 2. While the login page renders, Safari probes /apple-touch-icon.png (Accept: */*) on the same session.
        MvcResult probe = mockMvc.perform(get(TOUCH_ICON).session(session).header("Accept", "*/*")).andReturn();

        // 3. The saved request must still point at the user's real destination.
        SavedRequest saved = savedRequest(probe);
        assertThat(saved).as("saved request should survive the icon probe").isNotNull();
        assertThat(saved.getRedirectUrl()).as("icon probe must not hijack the post-login redirect").contains(PROTECTED_PAGE)
                .doesNotContain(TOUCH_ICON);
    }

    @Test
    @DisplayName("should not save an icon probe even when it claims to accept text/html")
    void iconPathIsNeverSavedRegardlessOfAcceptHeader() throws Exception {
        MvcResult probe = mockMvc.perform(get(TOUCH_ICON).header("Accept", BROWSER_ACCEPT)).andReturn();
        assertThat(savedRequest(probe)).as("auto-probed icon paths must never be saved").isNull();
    }

    @Test
    @DisplayName("should not save a request that does not accept text/html (e.g. Accept: image/png)")
    void nonHtmlRequestIsNotSaved() throws Exception {
        MvcResult probe = mockMvc.perform(get(PROTECTED_PAGE).header("Accept", "image/png")).andReturn();
        assertThat(savedRequest(probe)).as("non-HTML fetches are not user navigations").isNull();
    }

    @Test
    @DisplayName("should not save a request with no Accept header (resolves to */*, which is ignored)")
    void noAcceptHeaderIsNotSaved() throws Exception {
        // A missing Accept header resolves to */* via HeaderContentNegotiationStrategy; */* is in the ignored set, so
        // the request is not a confirmed HTML navigation and must not be saved. This exercises the one matcher branch
        // not covered by the explicit-Accept cases above.
        MvcResult probe = mockMvc.perform(get(PROTECTED_PAGE)).andReturn();
        assertThat(savedRequest(probe)).as("a request without an explicit text/html Accept is not saved").isNull();
    }

    @Test
    @DisplayName("should not save an HTMX partial request")
    void htmxRequestIsNotSaved() throws Exception {
        MvcResult probe = mockMvc.perform(get(PROTECTED_PAGE).header("Accept", BROWSER_ACCEPT).header("HX-Request", "true")).andReturn();
        assertThat(savedRequest(probe)).as("HTMX partials should not become post-login redirect targets").isNull();
    }

    @Test
    @DisplayName("should not save an XHR request")
    void xhrRequestIsNotSaved() throws Exception {
        MvcResult probe =
                mockMvc.perform(get(PROTECTED_PAGE).header("Accept", BROWSER_ACCEPT).header("X-Requested-With", "XMLHttpRequest")).andReturn();
        assertThat(savedRequest(probe)).as("XHR calls should not become post-login redirect targets").isNull();
    }

    @Test
    @DisplayName("should not save a POST request")
    void postRequestIsNotSaved() throws Exception {
        MvcResult probe = mockMvc.perform(post(PROTECTED_PAGE).header("Accept", BROWSER_ACCEPT).with(
                org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())).andReturn();
        assertThat(savedRequest(probe)).as("only GET navigations can be meaningfully replayed after login").isNull();
    }

    private SavedRequest savedRequest(MvcResult result) {
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        return session == null ? null : (SavedRequest) session.getAttribute(SAVED_REQUEST_ATTR);
    }
}
