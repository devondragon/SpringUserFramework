package com.digitalsanctuary.spring.user.listener;

import static org.mockito.Mockito.*;

import java.util.Collection;

import com.digitalsanctuary.spring.user.service.LoginAttemptService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationFailureDisabledEvent;
import org.springframework.security.authentication.event.AuthenticationFailureLockedEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthenticationEventListener Tests")
class AuthenticationEventListenerTest {

    @Mock
    private LoginAttemptService loginAttemptService;

    @InjectMocks
    private AuthenticationEventListener authenticationEventListener;

    private Authentication authentication;
    private String username;

    @BeforeEach
    void setUp() {
        username = "test@example.com";
        authentication = new UsernamePasswordAuthenticationToken(username, "password");
    }

    @Nested
    @DisplayName("Success Event Tests")
    class SuccessEventTests {

        @Test
        @DisplayName("onSuccess - updates login attempt service for successful authentication")
        void onSuccess_updatesLoginAttemptService() {
            // Given
            AuthenticationSuccessEvent event = new AuthenticationSuccessEvent(authentication);

            // When
            authenticationEventListener.onSuccess(event);

            // Then
            verify(loginAttemptService).loginSucceeded(username);
        }

        @Test
        @DisplayName("onSuccess - handles different authentication types")
        void onSuccess_handlesDifferentAuthTypes() {
            // Given
            Authentication oauthAuth = mock(Authentication.class);
            when(oauthAuth.getName()).thenReturn("oauth.user@example.com");
            AuthenticationSuccessEvent event = new AuthenticationSuccessEvent(oauthAuth);

            // When
            authenticationEventListener.onSuccess(event);

            // Then
            verify(loginAttemptService).loginSucceeded("oauth.user@example.com");
        }

        @Test
        @DisplayName("onSuccess - handles null username gracefully")
        void onSuccess_handlesNullUsername() {
            // Given
            Authentication nullAuth = new Authentication() {
                @Override
                public String getName() {
                    return null;
                }
                
                @Override
                public Collection<? extends GrantedAuthority> getAuthorities() {
                    return null;
                }
                
                @Override
                public Object getCredentials() {
                    return null;
                }
                
                @Override
                public Object getDetails() {
                    return null;
                }
                
                @Override
                public Object getPrincipal() {
                    return "unknown";
                }
                
                @Override
                public boolean isAuthenticated() {
                    return true;
                }
                
                @Override
                public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
                }
            };
            AuthenticationSuccessEvent event = new AuthenticationSuccessEvent(nullAuth);

            // When
            authenticationEventListener.onSuccess(event);

            // Then
            // Should use the principal string "unknown" when getName() is null
            verify(loginAttemptService).loginSucceeded("unknown");
        }

        @Test
        @DisplayName("onSuccess - handles completely null authentication")
        void onSuccess_handlesCompletelyNullAuthentication() {
            // Given
            Authentication nullAuth = mock(Authentication.class);
            when(nullAuth.getName()).thenReturn(null);
            when(nullAuth.getPrincipal()).thenReturn(null);
            AuthenticationSuccessEvent event = new AuthenticationSuccessEvent(nullAuth);

            // When
            authenticationEventListener.onSuccess(event);

            // Then
            // Should not call login service when we can't extract any username
            verify(loginAttemptService, never()).loginSucceeded(anyString());
        }
    }

    @Nested
    @DisplayName("Failure Event Tests")
    class FailureEventTests {

        @Test
        @DisplayName("onFailure - handles bad credentials failure")
        void onFailure_handlesBadCredentials() {
            // Given
            BadCredentialsException exception = new BadCredentialsException("Bad credentials");
            AbstractAuthenticationFailureEvent event = new AuthenticationFailureBadCredentialsEvent(authentication, exception);

            // When
            authenticationEventListener.onFailure(event);

            // Then
            verify(loginAttemptService).loginFailed(username);
        }

        @Test
        @DisplayName("onFailure - handles account disabled failure")
        void onFailure_handlesAccountDisabled() {
            // Given
            DisabledException exception = new DisabledException("Account disabled");
            AbstractAuthenticationFailureEvent event = new AuthenticationFailureDisabledEvent(authentication, exception);

            // When
            authenticationEventListener.onFailure(event);

            // Then
            verify(loginAttemptService).loginFailed(username);
        }

        @Test
        @DisplayName("onFailure - handles account locked failure")
        void onFailure_handlesAccountLocked() {
            // Given
            LockedException exception = new LockedException("Account locked");
            AbstractAuthenticationFailureEvent event = new AuthenticationFailureLockedEvent(authentication, exception);

            // When
            authenticationEventListener.onFailure(event);

            // Then
            verify(loginAttemptService).loginFailed(username);
        }

        @Test
        @DisplayName("onFailure - handles null authentication name")
        void onFailure_handlesNullAuthenticationName() {
            // Given
            Authentication nullAuth = mock(Authentication.class);
            when(nullAuth.getName()).thenReturn(null);
            when(nullAuth.getPrincipal()).thenReturn("unknown");
            BadCredentialsException exception = new BadCredentialsException("Bad credentials");
            AbstractAuthenticationFailureEvent event = new AuthenticationFailureBadCredentialsEvent(nullAuth, exception);

            // When
            authenticationEventListener.onFailure(event);

            // Then
            // Should use the principal string "unknown" when getName() is null
            verify(loginAttemptService).loginFailed("unknown");
        }

        @Test
        @DisplayName("onFailure - handles completely null authentication")
        void onFailure_handlesCompletelyNullAuthentication() {
            // Given
            Authentication nullAuth = mock(Authentication.class);
            when(nullAuth.getName()).thenReturn(null);
            when(nullAuth.getPrincipal()).thenReturn(null);
            BadCredentialsException exception = new BadCredentialsException("Bad credentials");
            AbstractAuthenticationFailureEvent event = new AuthenticationFailureBadCredentialsEvent(nullAuth, exception);

            // When
            authenticationEventListener.onFailure(event);

            // Then
            // Should not call login service when we can't extract any username
            verify(loginAttemptService, never()).loginFailed(anyString());
        }
    }

    @Nested
    @DisplayName("Multiple Event Tests")
    class MultipleEventTests {

        @Test
        @DisplayName("Multiple success events for same user")
        void multipleSuccessEvents_sameUser() {
            // Given
            AuthenticationSuccessEvent event1 = new AuthenticationSuccessEvent(authentication);
            AuthenticationSuccessEvent event2 = new AuthenticationSuccessEvent(authentication);

            // When
            authenticationEventListener.onSuccess(event1);
            authenticationEventListener.onSuccess(event2);

            // Then
            verify(loginAttemptService, times(2)).loginSucceeded(username);
        }

        @Test
        @DisplayName("Mixed success and failure events")
        void mixedSuccessAndFailureEvents() {
            // Given
            AuthenticationSuccessEvent successEvent = new AuthenticationSuccessEvent(authentication);
            BadCredentialsException exception = new BadCredentialsException("Bad credentials");
            AbstractAuthenticationFailureEvent failureEvent = new AuthenticationFailureBadCredentialsEvent(authentication, exception);

            // When
            authenticationEventListener.onFailure(failureEvent);
            authenticationEventListener.onSuccess(successEvent);

            // Then
            verify(loginAttemptService).loginFailed(username);
            verify(loginAttemptService).loginSucceeded(username);
        }

        @Test
        @DisplayName("Events for different users")
        void events_differentUsers() {
            // Given
            String user1 = "user1@example.com";
            String user2 = "user2@example.com";

            Authentication auth1 = new UsernamePasswordAuthenticationToken(user1, "password");
            Authentication auth2 = new UsernamePasswordAuthenticationToken(user2, "password");

            AuthenticationSuccessEvent successEvent1 = new AuthenticationSuccessEvent(auth1);
            BadCredentialsException exception = new BadCredentialsException("Bad credentials");
            AbstractAuthenticationFailureEvent failureEvent2 = new AuthenticationFailureBadCredentialsEvent(auth2, exception);

            // When
            authenticationEventListener.onSuccess(successEvent1);
            authenticationEventListener.onFailure(failureEvent2);

            // Then
            verify(loginAttemptService).loginSucceeded(user1);
            verify(loginAttemptService).loginFailed(user2);
        }
    }
}
