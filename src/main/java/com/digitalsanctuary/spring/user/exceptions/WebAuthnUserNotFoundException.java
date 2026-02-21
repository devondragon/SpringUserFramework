package com.digitalsanctuary.spring.user.exceptions;

/**
 * Exception thrown when an authenticated principal cannot be resolved to an application user.
 */
public class WebAuthnUserNotFoundException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public WebAuthnUserNotFoundException(String message) {
		super(message);
	}
}
