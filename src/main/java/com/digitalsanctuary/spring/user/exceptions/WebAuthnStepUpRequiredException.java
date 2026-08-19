package com.digitalsanctuary.spring.user.exceptions;

/**
 * Thrown when a credential-altering passkey operation needs step-up (re-)authentication that the caller has not
 * satisfied.
 *
 * <p>
 * Distinct from {@link WebAuthnReauthenticationException}, which reports a supplied credential being wrong. This one
 * reports that no recent proof of presence exists, so the client should re-run its login ceremony and retry. It maps to
 * HTTP 401 with the error code {@code step-up-required}.
 * </p>
 */
public class WebAuthnStepUpRequiredException extends WebAuthnException {

    private static final long serialVersionUID = 1L;

    /** Error code returned to clients, so a step-up prompt can be told apart from a wrong-credential failure. */
    public static final String ERROR_CODE = "step-up-required";

    /**
     * Creates a new exception with the given message.
     *
     * @param message the detail message
     */
    public WebAuthnStepUpRequiredException(final String message) {
        super(message);
    }
}
