package com.digitalsanctuary.spring.user.util;

import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CustomOAuth2AuthenticationEntryPoint implements AuthenticationEntryPoint {
    private final AuthenticationFailureHandler failureHandler;
    private final String redirectURL;

    public CustomOAuth2AuthenticationEntryPoint(AuthenticationFailureHandler failureHandler, String redirectURL) {
        this.failureHandler = failureHandler;
        this.redirectURL = redirectURL;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException,
                                                                                                                          ServletException {
        log.debug("CustomOAuth2AuthenticationEntryPoint.commence:" + "called with authException: {}", authException);
        System.out.println("CustomOAuth2AuthenticationEntryPoint.commence() called with authException: " + authException);
        if (authException instanceof OAuth2AuthenticationException && failureHandler != null) {
            // Use the failure handler to handle the exception and perform the redirect
            failureHandler.onAuthenticationFailure(request, response, authException);
        } else {
            // For other exceptions, redirect to the login page
            System.out.println("CustomOAuth2AuthenticationEntryPoint.commence() setting error.message: " + authException.getMessage());
            request.getSession().setAttribute("error.message", authException.getMessage());
            response.sendRedirect(redirectURL);
        }
    }

}
