package com.digitalsanctuary.spring.user.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Custom authentication failure handler to handle different authentication exceptions.
 * Specifically handles {@link LockedException} to redirect with a specific error message
 * for locked accounts. For other authentication failures, it redirects with a generic
 * invalid credentials message.
 */
@Slf4j
@Component
public class CustomAuthenticationFailureHandler implements AuthenticationFailureHandler{

    /**
     * Called when an authentication attempt fails.
     *
     * @param request        the request during which the authentication attempt occurred.
     * @param response       the response.
     * @param exception      the exception which caused the authentication failure.
     * @throws IOException      in the event of an I/O error
     * @throws ServletException in the event of a servlet related error
     */
    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        if (exception instanceof LockedException) {
            request.getSession().setAttribute("error.message", "Your account is locked. Please contact support.");
            response.sendRedirect("/login?error=locked");
        } else {
            request.getSession().setAttribute("error.message", "Invalid username or password.");
            response.sendRedirect("/login?error=true");
        }
    }
}
