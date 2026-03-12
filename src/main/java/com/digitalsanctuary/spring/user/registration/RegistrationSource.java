package com.digitalsanctuary.spring.user.registration;

/**
 * Identifies the origin of a registration attempt.
 *
 * @see RegistrationContext
 * @see RegistrationGuard
 */
public enum RegistrationSource {
    FORM, PASSWORDLESS, OAUTH2, OIDC
}
