package com.digitalsanctuary.spring.user.registration;

/**
 * Thrown when a {@link RegistrationGuard} denies a registration attempt.
 *
 * <p>This exception is raised from the service layer (e.g. {@code UserService}) so that the guard is
 * enforced exactly once for every registration path — form, passwordless, OAuth2, and OIDC — and can
 * never be bypassed by direct callers of the service registration methods. Callers (such as the REST
 * controller or the OAuth/OIDC user services) catch this exception and translate it into the
 * appropriate denial response for their transport.</p>
 *
 * <p>The {@link #getReason() reason} carries the human-readable denial message produced by the guard
 * via {@link RegistrationDecision#deny(String)}.</p>
 *
 * @see RegistrationGuard
 * @see RegistrationDecision
 */
public class RegistrationDeniedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** The human-readable reason the registration was denied. */
    private final String reason;

    /**
     * Instantiates a new registration denied exception.
     *
     * @param reason the human-readable denial reason from the guard
     */
    public RegistrationDeniedException(final String reason) {
        super(reason);
        this.reason = reason;
    }

    /**
     * Gets the human-readable reason the registration was denied.
     *
     * @return the denial reason
     */
    public String getReason() {
        return reason;
    }
}
