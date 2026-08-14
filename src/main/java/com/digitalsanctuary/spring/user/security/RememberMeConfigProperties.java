package com.digitalsanctuary.spring.user.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;
import lombok.ToString;

/**
 * Configuration properties for Spring Security "remember-me". Bound from
 * {@code user.security.remember-me.*} (relaxed binding also accepts the legacy
 * {@code user.security.rememberMe.*} spelling). Defaults mirror the shipped
 * {@code config/dsspringuserconfig.properties} values.
 */
@Data
@ConfigurationProperties(prefix = "user.security.remember-me")
public class RememberMeConfigProperties {

    /** Whether remember-me is enabled. */
    private boolean enabled = false;

    /**
     * The remember-me signing key. Excluded from {@code toString} so the secret never leaks through bean logging.
     * When null, Spring Security generates an ephemeral key at startup.
     */
    @ToString.Exclude
    private String key;

    /** Token validity in seconds. */
    private int tokenValiditySeconds = 1209600;

    /** Name of the remember-me request parameter. */
    private String rememberMeParameter = "remember-me";

    /** Name of the remember-me cookie. */
    private String rememberMeCookieName = "remember-me";

    /**
     * Whether the remember-me cookie is marked {@code Secure}. Left null (unset) by default so Spring Security's own
     * behavior applies: the cookie is secure whenever the request that created it was made over HTTPS.
     */
    private Boolean useSecureCookie;

    /** Whether persistent (database-backed) remember-me tokens are used instead of the hash-based scheme. */
    private boolean usePersistentTokens = false;
}
