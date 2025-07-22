package com.digitalsanctuary.spring.user.listener;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.service.DSUserDetails;
import com.digitalsanctuary.spring.user.service.LoginAttemptService;

/**
 * Test class for AuthenticationEventListener OAuth2 authentication handling.
 * Verifies that the listener correctly extracts usernames from different principal types.
 */
@ExtendWith(MockitoExtension.class)
class AuthenticationEventListenerOAuth2Test {

    @Mock
    private LoginAttemptService loginAttemptService;

    @Mock
    private OAuth2User oauth2User;

    @Mock
    private DSUserDetails dsUserDetails;

    @InjectMocks
    private AuthenticationEventListener authenticationEventListener;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setEmail("test@example.com");
        testUser.setFirstName("Test");
        testUser.setLastName("User");
    }

    @Test
    void onSuccess_withDSUserDetailsPrincipal_extractsEmailCorrectly() {
        // Given
        when(dsUserDetails.getUsername()).thenReturn("test@example.com");
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
            dsUserDetails, null, dsUserDetails.getAuthorities());
        AuthenticationSuccessEvent event = new AuthenticationSuccessEvent(auth);

        // When
        authenticationEventListener.onSuccess(event);

        // Then
        verify(loginAttemptService).loginSucceeded("test@example.com");
    }

    @Test
    void onSuccess_withOAuth2UserPrincipalWithEmail_extractsEmailCorrectly() {
        // Given
        when(oauth2User.getAttribute("email")).thenReturn("oauth@example.com");
        
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
            oauth2User, null, oauth2User.getAuthorities());
        AuthenticationSuccessEvent event = new AuthenticationSuccessEvent(auth);

        // When
        authenticationEventListener.onSuccess(event);

        // Then
        verify(loginAttemptService).loginSucceeded("oauth@example.com");
    }

    @Test
    void onSuccess_withOAuth2UserPrincipalWithoutEmail_fallsBackToName() {
        // Given
        when(oauth2User.getAttribute("email")).thenReturn(null);
        when(oauth2User.getName()).thenReturn("oauth_user_123");
        
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
            oauth2User, null, oauth2User.getAuthorities());
        AuthenticationSuccessEvent event = new AuthenticationSuccessEvent(auth);

        // When
        authenticationEventListener.onSuccess(event);

        // Then
        verify(loginAttemptService).loginSucceeded("oauth_user_123");
    }

    @Test
    void onSuccess_withStringPrincipal_extractsUsernameCorrectly() {
        // Given
        String username = "testuser@example.com";
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
            username, null);
        AuthenticationSuccessEvent event = new AuthenticationSuccessEvent(auth);

        // When
        authenticationEventListener.onSuccess(event);

        // Then
        verify(loginAttemptService).loginSucceeded(username);
    }

    @Test
    void onSuccess_withUnknownPrincipalType_fallsBackToGetName() {
        // Given
        Object unknownPrincipal = new Object() {
            @Override
            public String toString() {
                return "unknown_principal";
            }
        };
        
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
            unknownPrincipal, null);
        AuthenticationSuccessEvent event = new AuthenticationSuccessEvent(auth) {
            @Override
            public Authentication getAuthentication() {
                return new Authentication() {
                    @Override
                    public String getName() {
                        return "fallback@example.com";
                    }
                    
                    @Override
                    public Collection<? extends GrantedAuthority> getAuthorities() {
                        return auth.getAuthorities();
                    }
                    
                    @Override
                    public Object getCredentials() {
                        return auth.getCredentials();
                    }
                    
                    @Override
                    public Object getDetails() {
                        return auth.getDetails();
                    }
                    
                    @Override
                    public Object getPrincipal() {
                        return unknownPrincipal;
                    }
                    
                    @Override
                    public boolean isAuthenticated() {
                        return auth.isAuthenticated();
                    }
                    
                    @Override
                    public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
                        auth.setAuthenticated(isAuthenticated);
                    }
                };
            }
        };

        // When
        authenticationEventListener.onSuccess(event);

        // Then
        verify(loginAttemptService).loginSucceeded("fallback@example.com");
    }

    @Test
    void onSuccess_withNullUsername_doesNotCallLoginAttemptService() {
        // Given
        when(oauth2User.getAttribute("email")).thenReturn(null);
        when(oauth2User.getName()).thenReturn(null);
        
        // Create an authentication with a custom implementation that returns null for getName()
        Authentication auth = new Authentication() {
            @Override
            public String getName() {
                return null;
            }
            
            @Override
            public Collection<? extends GrantedAuthority> getAuthorities() {
                return oauth2User.getAuthorities();
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
                return oauth2User;
            }
            
            @Override
            public boolean isAuthenticated() {
                return true;
            }
            
            @Override
            public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
            }
        };
        
        AuthenticationSuccessEvent event = new AuthenticationSuccessEvent(auth);

        // When
        authenticationEventListener.onSuccess(event);

        // Then
        verify(loginAttemptService, never()).loginSucceeded(anyString());
    }

    @Test
    void onSuccess_withNullPrincipal_handlesGracefully() {
        // Given
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
            null, null);
        AuthenticationSuccessEvent event = new AuthenticationSuccessEvent(auth) {
            @Override
            public Authentication getAuthentication() {
                return new Authentication() {
                    @Override
                    public String getName() {
                        return "auth_name@example.com";
                    }
                    
                    @Override
                    public Collection<? extends GrantedAuthority> getAuthorities() {
                        return auth.getAuthorities();
                    }
                    
                    @Override
                    public Object getCredentials() {
                        return auth.getCredentials();
                    }
                    
                    @Override
                    public Object getDetails() {
                        return auth.getDetails();
                    }
                    
                    @Override
                    public Object getPrincipal() {
                        return null;
                    }
                    
                    @Override
                    public boolean isAuthenticated() {
                        return auth.isAuthenticated();
                    }
                    
                    @Override
                    public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
                        auth.setAuthenticated(isAuthenticated);
                    }
                };
            }
        };

        // When
        authenticationEventListener.onSuccess(event);

        // Then
        verify(loginAttemptService).loginSucceeded("auth_name@example.com");
    }
}