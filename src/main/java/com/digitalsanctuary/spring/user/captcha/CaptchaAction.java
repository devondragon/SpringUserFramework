package com.digitalsanctuary.spring.user.captcha;

/**
 * The framework API actions that can be CAPTCHA-protected.
 *
 * <p>
 * These are the unauthenticated, email-sending endpoints an abuser can drive without an account:
 * registration spam, password-reset flooding, and verification-email bombing. Each constant carries
 * the context-relative path of its action, which is the single source of truth for interceptor
 * registration ({@code CaptchaAutoConfiguration}), request matching
 * ({@code CaptchaValidationInterceptor}), and the per-action toggles in
 * {@link CaptchaConfigProperties.Protect}.
 * </p>
 *
 * <p>
 * The action is passed to {@link CaptchaService#verify(CaptchaContext)} so providers that bind a
 * token to the challenge it was issued for (reCAPTCHA v3 actions) or apply per-action score
 * thresholds can do so. Adding a protected action is a matter of adding a constant here and a
 * toggle on {@code Protect}.
 * </p>
 */
public enum CaptchaAction {

    /** {@code POST /user/registration} — new account registration. */
    REGISTRATION("/user/registration"),

    /**
     * {@code POST /user/registration/passwordless} — passkey-only account registration. Reachable
     * only when a WebAuthn credential management bean is present and the consumer has added the
     * path to {@code user.security.unprotectedURIs}, but when it is reachable it creates an account
     * and sends a verification email for an unauthenticated caller, exactly like
     * {@link #REGISTRATION} and without even requiring a password in the payload.
     */
    PASSWORDLESS_REGISTRATION("/user/registration/passwordless"),

    /** {@code POST /user/resetPassword} — request a password-reset email. */
    RESET_PASSWORD("/user/resetPassword"),

    /** {@code POST /user/resendRegistrationToken} — resend the verification email. */
    RESEND_REGISTRATION_TOKEN("/user/resendRegistrationToken");

    private final String path;

    CaptchaAction(String path) {
        this.path = path;
    }

    /**
     * Returns the context-relative request path of this action.
     *
     * @return the path, e.g. {@code /user/registration}
     */
    public String path() {
        return path;
    }
}
