package com.digitalsanctuary.spring.user.registration;

/**
 * Service Provider Interface (SPI) for gating user registration.
 *
 * <p>Implementations are called before a new user account is created across all
 * registration paths (form, passwordless, OAuth2, OIDC). If the guard denies a
 * registration, the user is not created and an appropriate error is returned.</p>
 *
 * <p>To activate a custom guard, declare a Spring bean that implements this interface.
 * The library's default (permit-all) guard is automatically replaced via
 * {@link org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean}.</p>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * @Component
 * public class InviteOnlyGuard implements RegistrationGuard {
 *     private final InviteCodeRepository inviteCodeRepository;
 *
 *     public InviteOnlyGuard(InviteCodeRepository inviteCodeRepository) {
 *         this.inviteCodeRepository = inviteCodeRepository;
 *     }
 *
 *     @Override
 *     public RegistrationDecision evaluate(RegistrationContext context) {
 *         // Allow all OAuth2/OIDC registrations
 *         if (context.source() == RegistrationSource.OAUTH2 || context.source() == RegistrationSource.OIDC) {
 *             return RegistrationDecision.allow();
 *         }
 *         // Require invite code for form/passwordless
 *         return RegistrationDecision.deny("Registration is by invitation only.");
 *     }
 * }
 * }</pre>
 *
 * <p><strong>Thread Safety:</strong> Implementations must be thread-safe as they may
 * be invoked concurrently from multiple request threads.</p>
 *
 * @see RegistrationContext
 * @see RegistrationDecision
 * @see DefaultRegistrationGuard
 */
@FunctionalInterface
public interface RegistrationGuard {

    /**
     * Evaluates whether a registration attempt should be allowed.
     *
     * @param context the registration context describing the attempt
     * @return a {@link RegistrationDecision} indicating whether registration is permitted
     */
    RegistrationDecision evaluate(RegistrationContext context);
}
