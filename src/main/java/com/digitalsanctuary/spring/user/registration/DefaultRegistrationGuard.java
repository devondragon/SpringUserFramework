package com.digitalsanctuary.spring.user.registration;

/**
 * Default {@link RegistrationGuard} that permits all registrations.
 *
 * <p>This implementation is automatically registered when the consuming application does not
 * define its own {@link RegistrationGuard} bean. To restrict registration, declare
 * a custom {@link RegistrationGuard} bean in your application context.</p>
 *
 * @see RegistrationGuard
 * @see RegistrationGuardConfiguration
 */
public class DefaultRegistrationGuard implements RegistrationGuard {

    @Override
    public RegistrationDecision evaluate(RegistrationContext context) {
        return RegistrationDecision.allow();
    }
}
