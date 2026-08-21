package com.digitalsanctuary.spring.user.event;

import com.digitalsanctuary.spring.user.persistence.model.User;
import lombok.Getter;
import lombok.ToString;
import org.springframework.context.ApplicationEvent;

/**
 * Published when a user registers a new WebAuthn credential (passkey).
 *
 * <p>
 * Enrolling a passkey grants a durable new way into the account, and it survives a password change, since session
 * invalidation ends sessions rather than credentials. Spring Security owns the endpoint that performs it
 * ({@code POST /webauthn/register}), so the framework observes enrollment where it writes through: the JPA
 * {@code UserCredentialRepository}. That catches every enrollment regardless of which endpoint triggered it.
 * </p>
 *
 * <p>
 * The event fires only for a genuinely new credential. Spring Security also saves through the same repository on
 * every successful assertion, to persist the updated signature count, and that is not a registration.
 * </p>
 */
@Getter
@ToString(callSuper = false)
public class WebAuthnCredentialRegisteredEvent extends ApplicationEvent {

    private static final long serialVersionUID = 1L;

    /** The user who registered the credential. */
    private final transient User user;

    /** The base64url credential id, useful for correlating with the audit log. */
    private final String credentialId;

    /** The user-supplied label for the credential, or {@code "Passkey"} when none was given. */
    private final String label;

    /**
     * Creates the event.
     *
     * @param source the component publishing the event
     * @param user the user who registered the credential
     * @param credentialId the base64url credential id
     * @param label the credential label
     */
    public WebAuthnCredentialRegisteredEvent(Object source, User user, String credentialId, String label) {
        super(source);
        this.user = user;
        this.credentialId = credentialId;
        this.label = label;
    }
}
