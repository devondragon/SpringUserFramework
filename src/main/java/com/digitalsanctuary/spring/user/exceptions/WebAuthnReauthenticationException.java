package com.digitalsanctuary.spring.user.exceptions;

/**
 * Thrown when re-authentication for a credential-altering WebAuthn operation fails because the supplied current password
 * is incorrect. Maps to HTTP 401 (Unauthorized) so clients can distinguish a wrong password from a malformed request
 * (missing field &rarr; 400) or a locked account (&rarr; 423). A missing/blank current-password field is a client error
 * and remains a plain {@link WebAuthnException} (400).
 */
public class WebAuthnReauthenticationException extends WebAuthnException {

	/** Serial Version UID. */
	private static final long serialVersionUID = 1L;

	/**
	 * Instantiates a new WebAuthn re-authentication exception.
	 *
	 * @param message the message
	 */
	public WebAuthnReauthenticationException(final String message) {
		super(message);
	}
}
