package com.digitalsanctuary.spring.user.exceptions;

/**
 * Exception thrown for WebAuthn-related errors.
 *
 * <p>
 * This is a checked exception used to signal WebAuthn-specific business logic errors such as:
 * </p>
 * <ul>
 * <li>Attempting to delete the last passkey when user has no password</li>
 * <li>Invalid credential label (empty, too long)</li>
 * <li>Credential not found or access denied</li>
 * <li>User not found during credential operations</li>
 * </ul>
 */
public class WebAuthnException extends Exception {

	/** Serial Version UID. */
	private static final long serialVersionUID = 1L;

	/**
	 * Instantiates a new WebAuthn exception.
	 */
	public WebAuthnException() {
		super();
	}

	/**
	 * Instantiates a new WebAuthn exception.
	 *
	 * @param message the message
	 * @param cause the cause
	 */
	public WebAuthnException(final String message, final Throwable cause) {
		super(message, cause);
	}

	/**
	 * Instantiates a new WebAuthn exception.
	 *
	 * @param message the message
	 */
	public WebAuthnException(final String message) {
		super(message);
	}

	/**
	 * Instantiates a new WebAuthn exception.
	 *
	 * @param cause the cause
	 */
	public WebAuthnException(final Throwable cause) {
		super(cause);
	}
}
