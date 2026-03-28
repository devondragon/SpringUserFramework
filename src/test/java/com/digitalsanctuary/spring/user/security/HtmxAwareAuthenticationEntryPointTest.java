package com.digitalsanctuary.spring.user.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@ExtendWith(MockitoExtension.class)
@DisplayName("HtmxAwareAuthenticationEntryPoint Tests")
class HtmxAwareAuthenticationEntryPointTest {

    @Mock
    private AuthenticationEntryPoint delegate;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private static final String LOGIN_URL = "/user/login";
    private HtmxAwareAuthenticationEntryPoint entryPoint;

    @BeforeEach
    void setUp() {
        entryPoint = new HtmxAwareAuthenticationEntryPoint(delegate, LOGIN_URL);
    }

    @Nested
    @DisplayName("HTMX Request Handling")
    class HtmxRequestHandling {

        private StringWriter responseBody;

        @BeforeEach
        void setUp() throws IOException {
            when(request.getHeader("HX-Request")).thenReturn("true");
            when(request.getContextPath()).thenReturn("");
            responseBody = new StringWriter();
            when(response.getWriter()).thenReturn(new PrintWriter(responseBody));
        }

        @Test
        @DisplayName("Should return 401 when HTMX request received")
        void shouldReturn401WhenHtmxRequestReceived() throws IOException, ServletException {
            // Given
            AuthenticationException authException = new InsufficientAuthenticationException("Session expired");

            // When
            entryPoint.commence(request, response, authException);

            // Then
            verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }

        @Test
        @DisplayName("Should set JSON content type with UTF-8 charset when HTMX request received")
        void shouldSetJsonContentTypeWhenHtmxRequestReceived() throws IOException, ServletException {
            // Given
            AuthenticationException authException = new InsufficientAuthenticationException("Session expired");

            // When
            entryPoint.commence(request, response, authException);

            // Then
            verify(response).setCharacterEncoding("UTF-8");
            verify(response).setContentType("application/json;charset=UTF-8");
        }

        @Test
        @DisplayName("Should set HX-Redirect header to login URL when HTMX request received")
        void shouldSetHxRedirectHeaderWhenHtmxRequestReceived() throws IOException, ServletException {
            // Given
            AuthenticationException authException = new InsufficientAuthenticationException("Session expired");

            // When
            entryPoint.commence(request, response, authException);

            // Then
            verify(response).setHeader("HX-Redirect", LOGIN_URL);
        }

        @Test
        @DisplayName("Should write JSON body with error details when HTMX request received")
        void shouldWriteJsonBodyWhenHtmxRequestReceived() throws IOException, ServletException {
            // Given
            AuthenticationException authException = new InsufficientAuthenticationException("Session expired");

            // When
            entryPoint.commence(request, response, authException);

            // Then
            String body = responseBody.toString();
            assertThat(body).contains("\"error\":\"authentication_required\"");
            assertThat(body).contains("\"message\":\"Session expired. Please log in.\"");
            assertThat(body).contains("\"loginUrl\":\"" + LOGIN_URL + "\"");
        }

        @Test
        @DisplayName("Should not call delegate when HTMX request received")
        void shouldNotCallDelegateWhenHtmxRequestReceived() throws IOException, ServletException {
            // Given
            AuthenticationException authException = new InsufficientAuthenticationException("Session expired");

            // When
            entryPoint.commence(request, response, authException);

            // Then
            verifyNoInteractions(delegate);
        }

        @Test
        @DisplayName("Should handle HX-Request header case insensitively")
        void shouldHandleHxRequestHeaderCaseInsensitively() throws IOException, ServletException {
            // Given
            when(request.getHeader("HX-Request")).thenReturn("TRUE");
            when(request.getContextPath()).thenReturn("");
            AuthenticationException authException = new InsufficientAuthenticationException("Session expired");

            // When
            entryPoint.commence(request, response, authException);

            // Then
            verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            verifyNoInteractions(delegate);
        }
    }

    @Nested
    @DisplayName("Servlet Context Path Handling")
    class ServletContextPathHandling {

        private StringWriter responseBody;

        @BeforeEach
        void setUp() throws IOException {
            when(request.getHeader("HX-Request")).thenReturn("true");
            responseBody = new StringWriter();
            when(response.getWriter()).thenReturn(new PrintWriter(responseBody));
        }

        @Test
        @DisplayName("Should prepend context path to HX-Redirect header when context path is non-empty")
        void shouldPrependContextPathToHxRedirectWhenContextPathIsNonEmpty() throws IOException, ServletException {
            // Given
            when(request.getContextPath()).thenReturn("/app");
            AuthenticationException authException = new InsufficientAuthenticationException("Session expired");

            // When
            entryPoint.commence(request, response, authException);

            // Then
            verify(response).setHeader("HX-Redirect", "/app" + LOGIN_URL);
        }

        @Test
        @DisplayName("Should include context path in JSON loginUrl when context path is non-empty")
        void shouldIncludeContextPathInJsonLoginUrlWhenContextPathIsNonEmpty() throws IOException, ServletException {
            // Given
            when(request.getContextPath()).thenReturn("/app");
            AuthenticationException authException = new InsufficientAuthenticationException("Session expired");

            // When
            entryPoint.commence(request, response, authException);

            // Then
            assertThat(responseBody.toString()).contains("\"loginUrl\":\"/app" + LOGIN_URL + "\"");
        }

        @Test
        @DisplayName("Should use login URL as-is when context path is empty")
        void shouldUseLoginUrlAsIsWhenContextPathIsEmpty() throws IOException, ServletException {
            // Given
            when(request.getContextPath()).thenReturn("");
            AuthenticationException authException = new InsufficientAuthenticationException("Session expired");

            // When
            entryPoint.commence(request, response, authException);

            // Then
            verify(response).setHeader("HX-Redirect", LOGIN_URL);
            assertThat(responseBody.toString()).contains("\"loginUrl\":\"" + LOGIN_URL + "\"");
        }
    }

    @Nested
    @DisplayName("Non-HTMX Request Handling")
    class NonHtmxRequestHandling {

        @Test
        @DisplayName("Should delegate when HX-Request header is absent")
        void shouldDelegateWhenHxRequestHeaderAbsent() throws IOException, ServletException {
            // Given
            when(request.getHeader("HX-Request")).thenReturn(null);
            AuthenticationException authException = new BadCredentialsException("Invalid credentials");

            // When
            entryPoint.commence(request, response, authException);

            // Then
            verify(delegate).commence(request, response, authException);
        }

        @Test
        @DisplayName("Should delegate when HX-Request header is false")
        void shouldDelegateWhenHxRequestHeaderIsFalse() throws IOException, ServletException {
            // Given
            when(request.getHeader("HX-Request")).thenReturn("false");
            AuthenticationException authException = new BadCredentialsException("Invalid credentials");

            // When
            entryPoint.commence(request, response, authException);

            // Then
            verify(delegate).commence(request, response, authException);
        }

        @Test
        @DisplayName("Should preserve authentication exception when delegating")
        void shouldPreserveAuthenticationExceptionWhenDelegating() throws IOException, ServletException {
            // Given
            when(request.getHeader("HX-Request")).thenReturn(null);
            InsufficientAuthenticationException authException =
                    new InsufficientAuthenticationException("Full authentication required");

            // When
            entryPoint.commence(request, response, authException);

            // Then
            verify(delegate).commence(request, response, authException);
        }

        @Test
        @DisplayName("Should not modify response when delegating")
        void shouldNotModifyResponseWhenDelegating() throws IOException, ServletException {
            // Given
            when(request.getHeader("HX-Request")).thenReturn(null);
            AuthenticationException authException = new BadCredentialsException("Invalid");

            // When
            entryPoint.commence(request, response, authException);

            // Then
            verify(response, never()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            verify(response, never()).setContentType(any());
            verify(response, never()).setHeader(any(), any());
        }
    }

    @Nested
    @DisplayName("Delegate Exception Propagation")
    class DelegateExceptionPropagation {

        @Test
        @DisplayName("Should propagate IOException from delegate")
        void shouldPropagateIOExceptionFromDelegate() throws IOException, ServletException {
            // Given
            when(request.getHeader("HX-Request")).thenReturn(null);
            AuthenticationException authException = new BadCredentialsException("Invalid");
            doThrow(new IOException("Network error")).when(delegate).commence(any(), any(), any());

            // When/Then
            assertThatThrownBy(() -> entryPoint.commence(request, response, authException))
                    .isInstanceOf(IOException.class)
                    .hasMessage("Network error");
        }

        @Test
        @DisplayName("Should propagate ServletException from delegate")
        void shouldPropagateServletExceptionFromDelegate() throws IOException, ServletException {
            // Given
            when(request.getHeader("HX-Request")).thenReturn(null);
            AuthenticationException authException = new BadCredentialsException("Invalid");
            doThrow(new ServletException("Servlet error")).when(delegate).commence(any(), any(), any());

            // When/Then
            assertThatThrownBy(() -> entryPoint.commence(request, response, authException))
                    .isInstanceOf(ServletException.class)
                    .hasMessage("Servlet error");
        }
    }

    @Nested
    @DisplayName("Response Committed Handling")
    class ResponseCommittedHandling {

        @Test
        @DisplayName("Should not write response when already committed")
        void shouldNotWriteResponseWhenAlreadyCommitted() throws IOException, ServletException {
            // Given
            when(request.getHeader("HX-Request")).thenReturn("true");
            when(response.isCommitted()).thenReturn(true);
            AuthenticationException authException = new InsufficientAuthenticationException("Session expired");

            // When
            entryPoint.commence(request, response, authException);

            // Then
            verify(response, never()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            verify(response, never()).getWriter();
            verifyNoInteractions(delegate);
        }
    }
}
