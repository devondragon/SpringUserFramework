package com.digitalsanctuary.spring.user.listener;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
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
public class AuthenticationEventLIstener {

    final private LoginAttemptService loginAttemptService;

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent success) {
        // Handle successful authentication, e.g. logging or auditing
        log.debug("Authentication success: " + success.getAuthentication().getName());
        String username = success.getAuthentication().getName();
        loginAttemptService.loginSucceeded(username);
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent failure) {
        // Handle unsuccessful authentication, e.g. logging or auditing
        log.debug("Authentication failure: " + failure.getException().getMessage());
        String username = failure.getAuthentication().getName();
        loginAttemptService.loginFailed(username);
    }

}
