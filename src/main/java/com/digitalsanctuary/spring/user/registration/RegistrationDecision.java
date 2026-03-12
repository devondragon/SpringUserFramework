package com.digitalsanctuary.spring.user.registration;

/**
 * The result of a {@link RegistrationGuard} evaluation indicating whether
 * a registration attempt should be allowed or denied.
 *
 * @param allowed whether the registration is permitted
 * @param reason  a human-readable denial reason (may be {@code null} when allowed)
 */
public record RegistrationDecision(boolean allowed, String reason) {

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
        return new RegistrationDecision(false, reason);
    }
}
