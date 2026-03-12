package com.digitalsanctuary.spring.user.registration;

import java.util.Objects;

/**
 * Immutable context passed to {@link RegistrationGuard#evaluate(RegistrationContext)}
 * describing the registration attempt being evaluated.
 *
 * @param email        the email address of the user attempting to register; may be {@code null}
 *                     if the OAuth2/OIDC provider does not expose the user's email
 * @param source       the registration path (form, passwordless, OAuth2, or OIDC); never {@code null}
 * @param providerName the OAuth2/OIDC provider registration ID (e.g. {@code "google"},
 *                     {@code "keycloak"}), or {@code null} for form/passwordless registrations
 */
public record RegistrationContext(String email, RegistrationSource source, String providerName) {

    public RegistrationContext {
        Objects.requireNonNull(source, "source must not be null");
    }
}
