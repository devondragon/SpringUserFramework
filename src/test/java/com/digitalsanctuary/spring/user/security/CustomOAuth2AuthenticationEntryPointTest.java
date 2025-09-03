package com.digitalsanctuary.spring.user.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomOAuth2AuthenticationEntryPoint Tests")
class CustomOAuth2AuthenticationEntryPointTest {

    @Mock
    private AuthenticationFailureHandler failureHandler;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private HttpSession session;

    private CustomOAuth2AuthenticationEntryPoint entryPoint;
    private String redirectURL = "/login?error=oauth2";

    @BeforeEach
    void setUp() {
        lenient().when(request.getSession()).thenReturn(session);
    }

    @Nested
    @DisplayName("OAuth2 Exception Handling Tests")
    class OAuth2ExceptionHandlingTests {

        @BeforeEach
        void setUp() {
            entryPoint = new CustomOAuth2AuthenticationEntryPoint(failureHandler, redirectURL);
        }

        @Test
        @DisplayName("Should delegate OAuth2 exceptions to failure handler")
        void shouldDelegateOAuth2ExceptionsToFailureHandler() throws IOException, ServletException {
            // Given
            OAuth2Error oauth2Error = new OAuth2Error("invalid_client", "Client authentication failed", null);
            OAuth2AuthenticationException authException = new OAuth2AuthenticationException(oauth2Error);

            // When
            entryPoint.commence(request, response, authException);

            // Then
            verify(failureHandler).onAuthenticationFailure(request, response, authException);
            verify(response, never()).sendRedirect(anyString());
            verify(session, never()).setAttribute(anyString(), any());
        }

        @Test
        @DisplayName("Should handle OAuth2 exception with detailed error")
        void shouldHandleOAuth2ExceptionWithDetailedError() throws IOException, ServletException {
            // Given
            OAuth2Error oauth2Error = new OAuth2Error(
                    "authorization_request_not_found",
                    "Authorization request not found",
                    "https://tools.ietf.org/html/rfc6749#section-4.1.2.1"
            );
            OAuth2AuthenticationException authException = new OAuth2AuthenticationException(oauth2Error, "Custom detail");

            // When
            entryPoint.commence(request, response, authException);

            // Then
            verify(failureHandler).onAuthenticationFailure(eq(request), eq(response), eq(authException));
        }

        @Test
        @DisplayName("Should handle OAuth2 exception when failure handler is null")
        void shouldHandleOAuth2ExceptionWhenFailureHandlerIsNull() throws IOException, ServletException {
            // Given
            entryPoint = new CustomOAuth2AuthenticationEntryPoint(null, redirectURL);
            OAuth2Error oauth2Error = new OAuth2Error("invalid_grant", "Invalid authorization grant", null);
            OAuth2AuthenticationException authException = new OAuth2AuthenticationException(oauth2Error);

            // When
            entryPoint.commence(request, response, authException);

            // Then
            verify(session).setAttribute("error.message", "Authentication failed. Please try again.");
            verify(response).sendRedirect(redirectURL);
        }

        @Test
        @DisplayName("Should propagate IOException from failure handler")
        void shouldPropagateIOExceptionFromFailureHandler() throws IOException, ServletException {
            // Given
            OAuth2Error oauth2Error = new OAuth2Error("server_error", "Server error", null);
            OAuth2AuthenticationException authException = new OAuth2AuthenticationException(oauth2Error);
            IOException ioException = new IOException("Network error");
            doThrow(ioException).when(failureHandler).onAuthenticationFailure(any(), any(), any());

            // When/Then
            assertThatThrownBy(() -> entryPoint.commence(request, response, authException))
                    .isInstanceOf(IOException.class)
                    .hasMessage("Network error");
        }

        @Test
        @DisplayName("Should propagate ServletException from failure handler")
        void shouldPropagateServletExceptionFromFailureHandler() throws IOException, ServletException {
            // Given
            OAuth2Error oauth2Error = new OAuth2Error("invalid_request", "Invalid request", null);
            OAuth2AuthenticationException authException = new OAuth2AuthenticationException(oauth2Error);
            ServletException servletException = new ServletException("Servlet error");
            doThrow(servletException).when(failureHandler).onAuthenticationFailure(any(), any(), any());

            // When/Then
            assertThatThrownBy(() -> entryPoint.commence(request, response, authException))
                    .isInstanceOf(ServletException.class)
                    .hasMessage("Servlet error");
        }
    }

    @Nested
    @DisplayName("Non-OAuth2 Exception Handling Tests")
    class NonOAuth2ExceptionHandlingTests {

        @BeforeEach
        void setUp() {
            entryPoint = new CustomOAuth2AuthenticationEntryPoint(failureHandler, redirectURL);
        }

        @Test
        @DisplayName("Should redirect to login for non-OAuth2 exceptions")
        void shouldRedirectToLoginForNonOAuth2Exceptions() throws IOException, ServletException {
            // Given
            AuthenticationException authException = new BadCredentialsException("Invalid credentials");

            // When
            entryPoint.commence(request, response, authException);

            // Then
            verify(session).setAttribute("error.message", "Authentication failed. Please try again.");
            verify(response).sendRedirect(redirectURL);
            verify(failureHandler, never()).onAuthenticationFailure(any(), any(), any());
        }

        @Test
        @DisplayName("Should handle InsufficientAuthenticationException")
        void shouldHandleInsufficientAuthenticationException() throws IOException, ServletException {
            // Given
            AuthenticationException authException = new InsufficientAuthenticationException("Full authentication required");

            // When
            entryPoint.commence(request, response, authException);

            // Then
            verify(session).setAttribute("error.message", "Authentication failed. Please try again.");
            verify(response).sendRedirect(redirectURL);
        }

        @Test
        @DisplayName("Should handle custom AuthenticationException")
        void shouldHandleCustomAuthenticationException() throws IOException, ServletException {
            // Given
            AuthenticationException authException = new AuthenticationException("Custom auth error") {};

            // When
            entryPoint.commence(request, response, authException);

            // Then
            verify(session).setAttribute("error.message", "Authentication failed. Please try again.");
            verify(response).sendRedirect(redirectURL);
        }

        @Test
        @DisplayName("Should propagate IOException from redirect")
        void shouldPropagateIOExceptionFromRedirect() throws IOException, ServletException {
            // Given
            AuthenticationException authException = new BadCredentialsException("Invalid");
            IOException ioException = new IOException("Redirect failed");
            doThrow(ioException).when(response).sendRedirect(anyString());

            // When/Then
            assertThatThrownBy(() -> entryPoint.commence(request, response, authException))
                    .isInstanceOf(IOException.class)
                    .hasMessage("Redirect failed");
        }
    }

    @Nested
    @DisplayName("URL Configuration Tests")
    class URLConfigurationTests {

        @Test
        @DisplayName("Should use custom redirect URL")
        void shouldUseCustomRedirectURL() throws IOException, ServletException {
            // Given
            String customURL = "/custom/login?error=true";
            entryPoint = new CustomOAuth2AuthenticationEntryPoint(failureHandler, customURL);
            AuthenticationException authException = new BadCredentialsException("Invalid");

            // When
            entryPoint.commence(request, response, authException);

            // Then
            verify(response).sendRedirect(customURL);
        }

        @Test
        @DisplayName("Should handle empty redirect URL")
        void shouldHandleEmptyRedirectURL() throws IOException, ServletException {
            // Given
            entryPoint = new CustomOAuth2AuthenticationEntryPoint(failureHandler, "");
            AuthenticationException authException = new BadCredentialsException("Invalid");

            // When
            entryPoint.commence(request, response, authException);

            // Then
            verify(response).sendRedirect("");
        }

        @Test
        @DisplayName("Should handle null redirect URL")
        void shouldHandleNullRedirectURL() throws IOException, ServletException {
            // Given
            entryPoint = new CustomOAuth2AuthenticationEntryPoint(failureHandler, null);
            AuthenticationException authException = new BadCredentialsException("Invalid");

            // When
            entryPoint.commence(request, response, authException);

            // Then
            verify(response).sendRedirect(null);
        }

        @Test
        @DisplayName("Should handle redirect URL with query parameters")
        void shouldHandleRedirectURLWithQueryParameters() throws IOException, ServletException {
            // Given
            String urlWithParams = "/login?error=oauth2&type=authentication";
            entryPoint = new CustomOAuth2AuthenticationEntryPoint(failureHandler, urlWithParams);
            AuthenticationException authException = new BadCredentialsException("Invalid");

            // When
            entryPoint.commence(request, response, authException);

            // Then
            verify(response).sendRedirect(urlWithParams);
        }
    }

    @Nested
    @DisplayName("Session Handling Tests")
    class SessionHandlingTests {

        @BeforeEach
        void setUp() {
            entryPoint = new CustomOAuth2AuthenticationEntryPoint(failureHandler, redirectURL);
        }

        @Test
        @DisplayName("Should set error message in session for non-OAuth2 exceptions")
        void shouldSetErrorMessageInSession() throws IOException, ServletException {
            // Given
            AuthenticationException authException = new BadCredentialsException("Invalid");

            // When
            entryPoint.commence(request, response, authException);

            // Then
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
            verify(session).setAttribute(keyCaptor.capture(), valueCaptor.capture());
            
            assertThat(keyCaptor.getValue()).isEqualTo("error.message");
            assertThat(valueCaptor.getValue()).isEqualTo("Authentication failed. Please try again.");
        }

        @Test
        @DisplayName("Should create new session if none exists")
        void shouldCreateNewSessionIfNoneExists() throws IOException, ServletException {
            // Given
            when(request.getSession()).thenReturn(session);
            AuthenticationException authException = new BadCredentialsException("Invalid");

            // When
            entryPoint.commence(request, response, authException);

            // Then
            verify(request).getSession();
            verify(session).setAttribute("error.message", "Authentication failed. Please try again.");
        }

        @Test
        @DisplayName("Should not set session attribute for OAuth2 exceptions with handler")
        void shouldNotSetSessionAttributeForOAuth2ExceptionsWithHandler() throws IOException, ServletException {
            // Given
            OAuth2Error oauth2Error = new OAuth2Error("invalid_client", "Client error", null);
            OAuth2AuthenticationException authException = new OAuth2AuthenticationException(oauth2Error);

            // When
            entryPoint.commence(request, response, authException);

            // Then
            verify(session, never()).setAttribute(anyString(), any());
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("Should handle complete OAuth2 flow with failure handler")
        void shouldHandleCompleteOAuth2FlowWithFailureHandler() throws IOException, ServletException {
            // Given
            entryPoint = new CustomOAuth2AuthenticationEntryPoint(failureHandler, redirectURL);
            OAuth2Error oauth2Error = new OAuth2Error(
                    "access_denied",
                    "User denied access",
                    "https://tools.ietf.org/html/rfc6749#section-4.1.2.1"
            );
            OAuth2AuthenticationException authException = new OAuth2AuthenticationException(oauth2Error, 
                    new BadCredentialsException("Underlying cause"));

            // When
            entryPoint.commence(request, response, authException);

            // Then
            ArgumentCaptor<OAuth2AuthenticationException> exceptionCaptor = 
                    ArgumentCaptor.forClass(OAuth2AuthenticationException.class);
            verify(failureHandler).onAuthenticationFailure(eq(request), eq(response), exceptionCaptor.capture());
            
            OAuth2AuthenticationException capturedEx = exceptionCaptor.getValue();
            assertThat(capturedEx.getError().getErrorCode()).isEqualTo("access_denied");
            assertThat(capturedEx.getError().getDescription()).isEqualTo("User denied access");
            assertThat(capturedEx.getCause()).isInstanceOf(BadCredentialsException.class);
        }

        @Test
        @DisplayName("Should handle complete non-OAuth2 flow")
        void shouldHandleCompleteNonOAuth2Flow() throws IOException, ServletException {
            // Given
            entryPoint = new CustomOAuth2AuthenticationEntryPoint(failureHandler, "/app/login");
            AuthenticationException authException = new BadCredentialsException("Invalid username or password");

            // When
            entryPoint.commence(request, response, authException);

            // Then
            verify(request).getSession();
            verify(session).setAttribute("error.message", "Authentication failed. Please try again.");
            verify(response).sendRedirect("/app/login");
            verify(failureHandler, never()).onAuthenticationFailure(any(), any(), any());
        }

        @Test
        @DisplayName("Should handle mixed scenario - OAuth2 exception without handler")
        void shouldHandleMixedScenario() throws IOException, ServletException {
            // Given
            entryPoint = new CustomOAuth2AuthenticationEntryPoint(null, redirectURL);
            OAuth2Error oauth2Error = new OAuth2Error("invalid_token", "Token expired", null);
            OAuth2AuthenticationException authException = new OAuth2AuthenticationException(oauth2Error);

            // When
            entryPoint.commence(request, response, authException);

            // Then
            verify(session).setAttribute("error.message", "Authentication failed. Please try again.");
            verify(response).sendRedirect(redirectURL);
        }
    }
}