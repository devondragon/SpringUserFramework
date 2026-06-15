package com.digitalsanctuary.spring.user.exceptions;

/**
 * Thrown when a credential-altering WebAuthn operation is rejected because the account is currently locked due to too
 * many failed authentication attempts. Maps to HTTP 423 (Locked) so clients can distinguish a temporary lockout from a
 * wrong password (&rarr; 401) or a malformed request (&rarr; 400).
 */
public class WebAuthnAccountLockedException extends WebAuthnException {

	/** Serial Version UID. */
	private static final long serialVersionUID = 1L;

	/**
	 * Instantiates a new WebAuthn account-locked exception.
	 *
	 * @param message the message
	 */
	public WebAuthnAccountLockedException(final String message) {
		super(message);
	}
}
