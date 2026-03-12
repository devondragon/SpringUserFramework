package com.digitalsanctuary.spring.user.registration;

/**
 * The result of a {@link RegistrationGuard} evaluation indicating whether
 * a registration attempt should be allowed or denied.
 *
 * <p>Use the static factory methods {@link #allow()} and {@link #deny(String)}
 * rather than the canonical constructor. The {@code reason} parameter is only
 * meaningful when {@code allowed} is {@code false}.</p>
 *
 * @param allowed whether the registration is permitted
 * @param reason  a human-readable denial reason; only meaningful when {@code allowed}
 *                is {@code false}, may be {@code null} when allowed
 */
public record RegistrationDecision(boolean allowed, String reason) {

    /** Default reason used when {@link #deny(String)} is called with a blank or null reason. */
    private static final String DEFAULT_DENIAL_REASON = "Registration denied.";

    /**
     * Creates a decision that allows the registration to proceed.
     *
     * @return an allowing decision with no reason
     */
    public static RegistrationDecision allow() {
        return new RegistrationDecision(true, null);
    }

    /**
     * Creates a decision that denies the registration.
     *
     * @param reason a human-readable explanation for the denial
     * @return a denying decision with the given reason
     */
    public static RegistrationDecision deny(String reason) {
        if (reason == null || reason.isBlank()) {
            reason = DEFAULT_DENIAL_REASON;
        }
        return new RegistrationDecision(false, reason);
    }
}
