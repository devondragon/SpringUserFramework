package com.digitalsanctuary.spring.user.security;

import static org.assertj.core.api.Assertions.assertThat;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

/**
 * Tests for {@link SanitizingOAuth2AuthenticationFailureHandler}, which must never leak raw exception
 * messages (which may contain account emails from Locked/Disabled exceptions) to the user-facing session.
 */
@DisplayName("SanitizingOAuth2AuthenticationFailureHandler Tests")
class SanitizingOAuth2AuthenticationFailureHandlerTest {

    private static final String LOGIN_PAGE_URI = "/user/login.html";
    private static final String SESSION_ATTRIBUTE = "error.message";

    private SanitizingOAuth2AuthenticationFailureHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SanitizingOAuth2AuthenticationFailureHandler(LOGIN_PAGE_URI);
    }

    @Test
    @DisplayName("Should store generic message (not raw exception) for LockedException with email")
    void shouldNotLeakRawMessageForLockedException() throws Exception {
        // Given - a LockedException whose message contains the account email (per Task 1.4)
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        LockedException raw = new LockedException("Account is locked for user secret.email@example.com");

        // When
        handler.onAuthenticationFailure(request, response, raw);

        // Then
        HttpSession session = request.getSession(false);
        assertThat(session).isNotNull();
        Object stored = session.getAttribute(SESSION_ATTRIBUTE);
        assertThat(stored).isInstanceOf(String.class);
        assertThat((String) stored).doesNotContain("secret.email@example.com");
        assertThat((String) stored).doesNotContain("Account is locked");
        assertThat((String) stored).isEqualTo(SanitizingOAuth2AuthenticationFailureHandler.GENERIC_FAILURE_MESSAGE);
        assertThat(response.getRedirectedUrl()).isEqualTo(LOGIN_PAGE_URI);
    }

    @Test
    @DisplayName("Should store generic message for arbitrary OAuth2AuthenticationException")
    void shouldNotLeakRawMessageForOAuth2Exception() throws Exception {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        OAuth2AuthenticationException raw = new OAuth2AuthenticationException(
                new OAuth2Error("User Registered With Alternate Provider"),
                "Looks like you're signed up with your GOOGLE account leaking@example.com");

        // When
        handler.onAuthenticationFailure(request, response, raw);

        // Then
        String stored = (String) request.getSession(false).getAttribute(SESSION_ATTRIBUTE);
        assertThat(stored).doesNotContain("leaking@example.com");
        assertThat(stored).isEqualTo(SanitizingOAuth2AuthenticationFailureHandler.GENERIC_FAILURE_MESSAGE);
        assertThat(response.getRedirectedUrl()).isEqualTo(LOGIN_PAGE_URI);
    }

    @Test
    @DisplayName("Should map email_not_verified error to a specific generic message")
    void shouldMapEmailNotVerifiedToSpecificGenericMessage() throws Exception {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        OAuth2AuthenticationException raw = new OAuth2AuthenticationException(
                new OAuth2Error("email_not_verified"), "Email verified=false for victim@example.com");

        // When
        handler.onAuthenticationFailure(request, response, raw);

        // Then
        String stored = (String) request.getSession(false).getAttribute(SESSION_ATTRIBUTE);
        assertThat(stored).doesNotContain("victim@example.com");
        assertThat(stored).isEqualTo(SanitizingOAuth2AuthenticationFailureHandler.EMAIL_NOT_VERIFIED_MESSAGE);
        assertThat(response.getRedirectedUrl()).isEqualTo(LOGIN_PAGE_URI);
    }
}
