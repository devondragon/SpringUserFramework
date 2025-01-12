package com.digitalsanctuary.spring.user.exceptions;

/**
 * Exception thrown when there is an error processing OAuth2 authentication.
 */
public class OAuth2AuthenticationProcessingException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new OAuth2AuthenticationProcessingException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public OAuth2AuthenticationProcessingException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new OAuth2AuthenticationProcessingException with the specified detail message.
     *
     * @param message the detail message
     */
    public OAuth2AuthenticationProcessingException(String message) {
        super(message);
    }

    /**
     * Constructs a new OAuth2AuthenticationProcessingException with the specified cause.
     *
     * @param cause the cause of the exception
     */
    public OAuth2AuthenticationProcessingException(Throwable cause) {
        super(cause);
    }
}
