package com.digitalsanctuary.spring.user.security;

import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * The CustomOAuth2AuthenticationEntryPoint class is used to handle OAuth2 authentication exceptions. This class will redirect the user to the login
 * page if an exception occurs during the OAuth2 authentication process.
 */
@Slf4j
public class CustomOAuth2AuthenticationEntryPoint implements AuthenticationEntryPoint {
    private final AuthenticationFailureHandler failureHandler;
    private final String redirectURL;

    /**
     * Instantiates a new custom OAuth2 authentication entry point.
     *
     * @param failureHandler the failure handler
     * @param redirectURL the redirect URL
     */
    public CustomOAuth2AuthenticationEntryPoint(AuthenticationFailureHandler failureHandler, String redirectURL) {
        this.failureHandler = failureHandler;
        this.redirectURL = redirectURL;
    }

    /**
     * Commence. This method is called when an exception occurs during the OAuth2 authentication process. It will redirect the user to the login page
     * if an exception occurs.
     *
     * @param request the request
     * @param response the response
     * @param authException the auth exception
     * @throws IOException Signals that an I/O exception has occurred.
     * @throws ServletException the servlet exception
     */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException,
                                                                                                                          ServletException {
        // Log detailed exception information internally for debugging
        log.warn("OAuth2 authentication failed: {}", authException.getMessage(), authException);
        
        if (authException instanceof OAuth2AuthenticationException && failureHandler != null) {
            // Use the failure handler to handle the exception and perform the redirect
            failureHandler.onAuthenticationFailure(request, response, authException);
        } else {
            // For other exceptions, redirect to the login page with a generic error message
            String userFriendlyMessage = "Authentication failed. Please try again.";
            request.getSession().setAttribute("error.message", userFriendlyMessage);
            response.sendRedirect(redirectURL);
        }
    }

}
