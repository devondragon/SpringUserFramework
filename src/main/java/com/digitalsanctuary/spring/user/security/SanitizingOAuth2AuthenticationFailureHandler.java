package com.digitalsanctuary.spring.user.security;

import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * OAuth2/OIDC login failure handler that stores a GENERIC, user-safe message in the session for the UI to
 * display, while logging the real exception detail server-side.
 *
 * <p>
 * Raw {@link AuthenticationException} messages can leak sensitive detail to the browser. In particular, the
 * {@code LockedException}/{@code DisabledException} messages thrown during OAuth2/OIDC login (see Task 1.4)
 * embed the account email, and provider-conflict messages reveal which provider an account is registered with.
 * This handler ensures none of that raw detail reaches the user-facing {@code error.message} session attribute.
 * </p>
 *
 * <p>
 * The full exception (including its message and stack trace) is logged at {@code error}/{@code debug} level so
 * operators retain the diagnostic detail server-side, where it is an acceptable place for it.
 * </p>
 */
@Slf4j
public class SanitizingOAuth2AuthenticationFailureHandler implements AuthenticationFailureHandler {

    /** Session attribute key the login page reads to display an error message. */
    static final String ERROR_MESSAGE_SESSION_ATTRIBUTE = "error.message";

    /** OAuth2 error code raised when a provider explicitly reports the email is not verified. */
    static final String EMAIL_NOT_VERIFIED_ERROR_CODE = "email_not_verified";

    /** Generic message shown to the user for any unspecified authentication failure. */
    public static final String GENERIC_FAILURE_MESSAGE = "Authentication failed. Please try again.";

    /** Slightly more specific (but still non-sensitive) message for unverified-email failures. */
    public static final String EMAIL_NOT_VERIFIED_MESSAGE =
            "Your email address is not verified with your login provider. Please verify it and try again.";

    /** The login page URI to redirect to after a failure. */
    private final String loginPageURI;

    /**
     * Creates a new handler.
     *
     * @param loginPageURI the URI to redirect the user back to after a failed login
     */
    public SanitizingOAuth2AuthenticationFailureHandler(String loginPageURI) {
        this.loginPageURI = loginPageURI;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException, ServletException {
        // Log the real detail server-side only. Server logs are an acceptable place for sensitive detail.
        log.error("OAuth2 login failure: {}", exception.getMessage());
        log.debug("OAuth2 login failure detail", exception);

        // Store ONLY a generic, non-sensitive message for the UI. Never the raw exception message.
        request.getSession().setAttribute(ERROR_MESSAGE_SESSION_ATTRIBUTE, resolveUserFacingMessage(exception));
        response.sendRedirect(loginPageURI);
    }

    /**
     * Maps an authentication failure to a safe, user-facing message. A small number of failure categories map to
     * a slightly more helpful (but still non-sensitive) message; everything else falls back to a fixed generic
     * message.
     *
     * @param exception the authentication failure
     * @return a generic, non-sensitive message safe to display to the user
     */
    private String resolveUserFacingMessage(AuthenticationException exception) {
        if (exception instanceof OAuth2AuthenticationException oauth2Exception && oauth2Exception.getError() != null
                && EMAIL_NOT_VERIFIED_ERROR_CODE.equals(oauth2Exception.getError().getErrorCode())) {
            return EMAIL_NOT_VERIFIED_MESSAGE;
        }
        return GENERIC_FAILURE_MESSAGE;
    }
}
