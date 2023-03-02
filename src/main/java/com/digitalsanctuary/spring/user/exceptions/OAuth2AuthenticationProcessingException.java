package com.digitalsanctuary.spring.user.exceptions;

public class OAuth2AuthenticationProcessingException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public OAuth2AuthenticationProcessingException(String message, Throwable cause) {
        super(message, cause);
    }

    public OAuth2AuthenticationProcessingException(String message) {
        super(message);
    }

    public OAuth2AuthenticationProcessingException(Throwable cause) {
        super(cause);
    }
}
