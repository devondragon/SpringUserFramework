package com.digitalsanctuary.spring.user.captcha;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * Configuration properties for optional CAPTCHA verification on the framework's unauthenticated,
 * email-sending API actions. Bound from the {@code user.security.captcha} prefix.
 *
 * <p>
 * Disabled by default: with {@code user.security.captcha.enabled=false} the framework registers no
 * CAPTCHA interceptor or provider beans, no requests are inspected, and behavior is identical to
 * previous releases. This properties class and {@link CaptchaStartupValidator} are always
 * registered; the validator early-returns when disabled.
 * </p>
 */
@Data
@ConfigurationProperties(prefix = "user.security.captcha")
public class CaptchaConfigProperties {

    /**
     * Master switch for CAPTCHA verification. When false (the default), no CAPTCHA interceptor or
     * provider beans are registered and no requests are checked. Read at startup only: changing
     * this field on the bound bean at runtime does not turn interception on or off, because the
     * interceptor and provider beans are created conditionally during context refresh.
     */
    private boolean enabled = false;

    /**
     * The CAPTCHA provider. Only "turnstile" (Cloudflare Turnstile via
     * com.digitalsanctuary:ds-spring-cf-turnstile) is currently supported. Consumers may also
     * supply their own {@link CaptchaService} bean, which takes precedence.
     */
    private String provider = "turnstile";

    /**
     * Whether to start even when the configured provider reports it cannot verify anything (see
     * {@link CaptchaService#configurationErrors()}) — for example a missing Turnstile secret or
     * site key. False by default: such a provider rejects every request to every protected
     * endpoint, so failing startup surfaces the misconfiguration instead of shipping an outage
     * that looks healthy. Set true to boot anyway and take the WARN banner instead.
     */
    private boolean allowUnusableProvider = false;

    /** Per-action protection toggles, effective only when {@link #enabled} is true. */
    private Protect protect = new Protect();

    /**
     * Per-action CAPTCHA toggles. All three unauthenticated email-sending actions default to
     * protected once the master switch is on.
     */
    @Data
    public static class Protect {

        /** Require CAPTCHA on POST /user/registration. */
        private boolean registration = true;

        /** Require CAPTCHA on POST /user/registration/passwordless. */
        private boolean passwordlessRegistration = true;

        /** Require CAPTCHA on POST /user/resetPassword. */
        private boolean resetPassword = true;

        /** Require CAPTCHA on POST /user/resendRegistrationToken. */
        private boolean resendRegistrationToken = true;

        /**
         * Whether the given action requires a CAPTCHA. Unknown actions are treated as protected so
         * that adding a {@link CaptchaAction} constant without a matching toggle here fails closed
         * rather than silently leaving the new action unprotected.
         *
         * @param action the action to check; must not be null
         * @return true when the action requires a valid CAPTCHA token
         */
        public boolean isProtected(CaptchaAction action) {
            return switch (action) {
                case REGISTRATION -> registration;
                case PASSWORDLESS_REGISTRATION -> passwordlessRegistration;
                case RESET_PASSWORD -> resetPassword;
                case RESEND_REGISTRATION_TOKEN -> resendRegistrationToken;
            };
        }
    }
}
