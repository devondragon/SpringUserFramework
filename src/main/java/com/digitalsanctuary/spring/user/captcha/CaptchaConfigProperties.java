package com.digitalsanctuary.spring.user.captcha;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * Configuration properties for optional CAPTCHA verification on the framework's unauthenticated,
 * email-sending API actions. Bound from the {@code user.security.captcha} prefix.
 *
 * <p>
 * Disabled by default: with {@code user.security.captcha.enabled=false} the framework registers no
 * CAPTCHA beans and behavior is identical to previous releases.
 * </p>
 */
@Data
@ConfigurationProperties(prefix = "user.security.captcha")
public class CaptchaConfigProperties {

    /**
     * Master switch for CAPTCHA verification. When false (the default), no CAPTCHA beans are
     * registered and no requests are checked.
     */
    private boolean enabled = false;

    /**
     * The CAPTCHA provider. Only "turnstile" (Cloudflare Turnstile via
     * com.digitalsanctuary:ds-spring-cf-turnstile) is currently supported. Consumers may also
     * supply their own {@link CaptchaService} bean, which takes precedence.
     */
    private String provider = "turnstile";

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

        /** Require CAPTCHA on POST /user/resetPassword. */
        private boolean resetPassword = true;

        /** Require CAPTCHA on POST /user/resendRegistrationToken. */
        private boolean resendRegistrationToken = true;
    }
}
