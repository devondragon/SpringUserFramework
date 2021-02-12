package com.digitalsanctuary.spring.user.exceptions;

/**
 * The Class UserAlreadyExistException.
 */
public class UserAlreadyExistException extends RuntimeException {

	/** The Constant serialVersionUID. */
	private static final long serialVersionUID = -1644467856346594869L;

	/**
	 * Instantiates a new user already exist exception.
	 */
	public UserAlreadyExistException() {
		super();
	}

	/**
	 * Instantiates a new user already exist exception.
	 *
	 * @param message
	 *            the message
	 * @param cause
	 *            the cause
	 */
	public UserAlreadyExistException(final String message, final Throwable cause) {
		super(message, cause);
	}

	/**
	 * Instantiates a new user already exist exception.
	 *
	 * @param message
	 *            the message
	 */
	public UserAlreadyExistException(final String message) {
		super(message);
	}

	/**
	 * Instantiates a new user already exist exception.
	 *
	 * @param cause
	 *            the cause
	 */
	public UserAlreadyExistException(final Throwable cause) {
		super(cause);
	}
}
