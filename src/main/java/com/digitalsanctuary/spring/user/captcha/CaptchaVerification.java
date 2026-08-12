package com.digitalsanctuary.spring.user.captcha;

import java.util.Objects;

/**
 * The result of a {@link CaptchaService#verify(CaptchaContext)} call.
 *
 * <p>
 * The three-way outcome exists so the framework, not the provider implementation, decides what
 * happens when verification could not be completed. A {@code boolean} cannot distinguish "this
 * caller failed the challenge" from "the provider was unreachable", which leaves each implementer
 * to invent that policy — and the tempting choice during a vendor outage (allow the request so
 * signups keep working) silently disables the protection. With {@link Outcome#ERROR} the
 * implementation reports what happened and the framework applies the fail-closed rule, so an
 * implementation cannot fail open by accident.
 * </p>
 *
 * @param outcome what the provider determined; never null
 * @param detail optional human-readable detail for logging, null when there is nothing to add
 */
public record CaptchaVerification(Outcome outcome, String detail) {

    /** What the provider determined about a token. */
    public enum Outcome {

        /** The provider positively verified the token. The request proceeds. */
        VERIFIED,

        /** The provider actively rejected the token (invalid, expired, already used). */
        REJECTED,

        /**
         * Verification could not be completed (provider unreachable, misconfigured, malformed
         * response). Treated as a rejection by the framework.
         */
        ERROR
    }

    /**
     * Canonical constructor.
     *
     * @param outcome what the provider determined; must not be null
     * @param detail optional detail for logging
     */
    public CaptchaVerification {
        Objects.requireNonNull(outcome, "outcome must not be null");
    }

    /**
     * The token was positively verified.
     *
     * @return a VERIFIED result
     */
    public static CaptchaVerification verified() {
        return new CaptchaVerification(Outcome.VERIFIED, null);
    }

    /**
     * The provider actively rejected the token.
     *
     * @param detail why it was rejected, for logging; may be null
     * @return a REJECTED result
     */
    public static CaptchaVerification rejected(String detail) {
        return new CaptchaVerification(Outcome.REJECTED, detail);
    }

    /**
     * Verification could not be completed. The framework rejects the request.
     *
     * @param detail what went wrong, for logging; may be null
     * @return an ERROR result
     */
    public static CaptchaVerification error(String detail) {
        return new CaptchaVerification(Outcome.ERROR, detail);
    }

    /**
     * Whether the request may proceed. True only for {@link Outcome#VERIFIED}.
     *
     * @return true if the token was positively verified
     */
    public boolean isVerified() {
        return outcome == Outcome.VERIFIED;
    }
}
