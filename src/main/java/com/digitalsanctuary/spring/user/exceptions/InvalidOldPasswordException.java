package com.digitalsanctuary.spring.user.exceptions;

/**
 * The Class InvalidOldPasswordException.
 */
public class InvalidOldPasswordException extends RuntimeException {

	/** The Constant serialVersionUID. */
	private static final long serialVersionUID = -4000903219451476692L;

	/**
	 * Instantiates a new invalid old password exception.
	 */
	public InvalidOldPasswordException() {
		super();
	}

	/**
	 * Instantiates a new invalid old password exception.
	 *
	 * @param message
	 *            the message
	 * @param cause
	 *            the cause
	 */
	public InvalidOldPasswordException(final String message, final Throwable cause) {
		super(message, cause);
	}

	/**
	 * Instantiates a new invalid old password exception.
	 *
	 * @param message
	 *            the message
	 */
	public InvalidOldPasswordException(final String message) {
		super(message);
	}

	/**
	 * Instantiates a new invalid old password exception.
	 *
	 * @param cause
	 *            the cause
	 */
	public InvalidOldPasswordException(final Throwable cause) {
		super(cause);
	}

}
