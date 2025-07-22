package com.digitalsanctuary.spring.user.listener;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import com.digitalsanctuary.spring.user.service.DSUserDetails;
import com.digitalsanctuary.spring.user.service.LoginAttemptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * This class is used to listen for authentication events and handle account lockout functionality if needed.
 *
 * https://github.com/devondragon/SpringUserFramework/issues/29
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class AuthenticationEventListener {

    final private LoginAttemptService loginAttemptService;

    /**
     * This method listens for successful authentications and handles account lockout functionality.
     * It properly handles different authentication types including form login, OAuth2, and OIDC.
     *
     * @param success the success event
     */
    @EventListener
    public void onSuccess(AuthenticationSuccessEvent success) {
        // Extract username/email based on the principal type
        String username = null;
        Object principal = success.getAuthentication().getPrincipal();
        
        if (principal instanceof DSUserDetails) {
            // Form login or custom authentication
            username = ((DSUserDetails) principal).getUsername();
            log.debug("Authentication success for DSUserDetails: {}", username);
        } else if (principal instanceof OAuth2User) {
            // OAuth2/OIDC authentication - try to get email
            OAuth2User oauth2User = (OAuth2User) principal;
            username = oauth2User.getAttribute("email");
            if (username == null) {
                // Fallback to name if email is not available
                username = oauth2User.getName();
            }
            log.debug("Authentication success for OAuth2User: {}", username);
        } else if (principal instanceof String) {
            // Basic authentication or remember-me
            username = (String) principal;
            log.debug("Authentication success for String principal: {}", username);
        } else {
            // Fallback to getName() method for unknown principal types
            username = success.getAuthentication().getName();
            log.debug("Authentication success for unknown principal type {}: {}", 
                     principal != null ? principal.getClass().getName() : "null", username);
        }
        
        // Only process login success if we have a valid username
        if (username != null && !username.trim().isEmpty()) {
            loginAttemptService.loginSucceeded(username);
        } else {
            log.warn("Could not extract valid username from authentication event - not tracking");
        }
    }

    /**
     * This method listens for authentication failures and handles account lockout functionality.
     * It properly handles different authentication types including form login, OAuth2, and OIDC.
     *
     * @param failure the failure event
     */
    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent failure) {
        // For failures, try to get username from getName() first (the attempted username)
        String username = failure.getAuthentication().getName();
        
        // If getName() is null/empty, try to extract from principal
        if (username == null || username.trim().isEmpty()) {
            Object principal = failure.getAuthentication().getPrincipal();
            if (principal instanceof String) {
                username = (String) principal;
            }
        }
        
        // Only process login failure if we have a valid username
        if (username != null && !username.trim().isEmpty()) {
            log.debug("Authentication failure for user '{}': {}", username, failure.getException().getMessage());
            loginAttemptService.loginFailed(username);
        } else {
            log.warn("Could not extract valid username from authentication failure event - not tracking");
        }
    }

}
