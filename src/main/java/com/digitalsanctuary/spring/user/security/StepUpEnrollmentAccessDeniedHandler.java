package com.digitalsanctuary.spring.user.security;

import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import com.digitalsanctuary.spring.user.exceptions.WebAuthnStepUpRequiredException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Renders a step-up denial on the passkey enrollment endpoint the way the credential-management endpoints do: HTTP
 * 401 with a JSON body carrying the {@code step-up-required} error code, so a client can launch its login ceremony
 * and retry rather than receiving a bare 403 it cannot interpret.
 *
 * <p>
 * The enrollment gate is a filter-chain {@code authorizeHttpRequests} rule, so its denial is raised before any
 * controller runs and never reaches {@code WebAuthnManagementAPIAdvice}. Only a freshness denial (an
 * {@link AuthorizationDeniedException}) is treated as step-up; anything else that can deny the endpoint, such as a
 * CSRF failure, is passed to the delegate so it keeps its normal 403.
 * </p>
 */
@RequiredArgsConstructor
public class StepUpEnrollmentAccessDeniedHandler implements AccessDeniedHandler {

    /** Handles denials on the endpoint that are not the freshness gate (for example, CSRF). */
    private final AccessDeniedHandler delegate;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException, ServletException {
        if (!(accessDeniedException instanceof AuthorizationDeniedException)) {
            delegate.handle(request, response, accessDeniedException);
            return;
        }
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        // The message and error code are compile-time constants with no characters that need JSON escaping, so the
        // body is assembled directly. This keeps the handler independent of whichever Jackson version the consuming
        // application ships, matching the {message, error} shape GenericResponse serializes for the sibling endpoints.
        response.getWriter().write("{\"message\":\"Recent authentication is required to add a passkey. "
                + "Please verify with your passkey or password and retry.\",\"error\":\"" + WebAuthnStepUpRequiredException.ERROR_CODE
                + "\"}");
    }
}
