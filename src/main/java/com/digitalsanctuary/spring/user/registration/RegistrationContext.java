package com.digitalsanctuary.spring.user.registration;

/**
 * Immutable context passed to {@link RegistrationGuard#evaluate(RegistrationContext)}
 * describing the registration attempt being evaluated.
 *
 * @param email        the email address of the user attempting to register
 * @param source       the registration path (form, passwordless, OAuth2, or OIDC)
 * @param providerName the OAuth2/OIDC provider registration ID (e.g. {@code "google"},
 *                     {@code "keycloak"}), or {@code null} for form/passwordless registrations
 */
public record RegistrationContext(String email, RegistrationSource source, String providerName) {
}
