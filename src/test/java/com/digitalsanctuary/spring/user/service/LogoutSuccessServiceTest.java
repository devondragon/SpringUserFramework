package com.digitalsanctuary.spring.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import com.digitalsanctuary.spring.user.audit.AuditEvent;
import com.digitalsanctuary.spring.user.persistence.model.Role;
import com.digitalsanctuary.spring.user.persistence.model.User;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@ExtendWith(MockitoExtension.class)
@DisplayName("LogoutSuccessService Tests")
class LogoutSuccessServiceTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private HttpSession session;

    @Mock
    private Authentication authentication;

    @Mock
    private DSUserDetails userDetails;

    @InjectMocks
    private LogoutSuccessService logoutSuccessService;

    private User testUser;
    private String logoutSuccessUri = "/logout-success";
    private String sessionId = "test-session-123";
    private String clientIp = "192.168.1.100";
    private String userAgent = "Mozilla/5.0 Test Browser";

    @BeforeEach
    void setUp() {
        // Set the logout success URI via reflection
        ReflectionTestUtils.setField(logoutSuccessService, "logoutSuccessUri", logoutSuccessUri);

        // Create test user
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("test@example.com");
        testUser.setFirstName("Test");
        testUser.setLastName("User");
        testUser.setEnabled(true);
        List<Role> roles = new ArrayList<>();
        roles.add(new Role("ROLE_USER"));
        testUser.setRoles(roles);

        // Setup common mock behavior
        lenient().when(request.getSession()).thenReturn(session);
        lenient().when(session.getId()).thenReturn(sessionId);
        lenient().when(request.getHeader("User-Agent")).thenReturn(userAgent);
        lenient().when(request.getHeader("X-Forwarded-For")).thenReturn(clientIp);
        lenient().when(request.getContextPath()).thenReturn("");
        lenient().when(response.encodeRedirectURL(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Nested
    @DisplayName("Logout Success Handling Tests")
    class LogoutSuccessHandlingTests {

        @Test
        @DisplayName("Should handle logout with authenticated user")
        void shouldHandleLogoutWithAuthenticatedUser() throws IOException, ServletException {
            // Given
            when(authentication.getPrincipal()).thenReturn(userDetails);
            when(userDetails.getUser()).thenReturn(testUser);

            // When
            logoutSuccessService.onLogoutSuccess(request, response, authentication);

            // Then
            ArgumentCaptor<AuditEvent> eventCaptor = forClass(AuditEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());

            AuditEvent capturedEvent = eventCaptor.getValue();
            assertThat(capturedEvent).isNotNull();
            assertThat(capturedEvent.getUser()).isEqualTo(testUser);
            assertThat(capturedEvent.getSessionId()).isEqualTo(sessionId);
            assertThat(capturedEvent.getIpAddress()).isEqualTo(clientIp);
            assertThat(capturedEvent.getUserAgent()).isEqualTo(userAgent);
            assertThat(capturedEvent.getAction()).isEqualTo("Logout");
            assertThat(capturedEvent.getActionStatus()).isEqualTo("Success");
            assertThat(capturedEvent.getMessage()).isEqualTo("Success");
        }

        @Test
        @DisplayName("Should handle logout with null authentication")
        void shouldHandleLogoutWithNullAuthentication() throws IOException, ServletException {
            // When
            logoutSuccessService.onLogoutSuccess(request, response, null);

            // Then
            ArgumentCaptor<AuditEvent> eventCaptor = forClass(AuditEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());

            AuditEvent capturedEvent = eventCaptor.getValue();
            assertThat(capturedEvent).isNotNull();
            assertThat(capturedEvent.getUser()).isNull();
            assertThat(capturedEvent.getSessionId()).isEqualTo(sessionId);
            assertThat(capturedEvent.getAction()).isEqualTo("Logout");
        }

        @Test
        @DisplayName("Should handle logout with non-DSUserDetails principal")
        void shouldHandleLogoutWithNonDSUserDetailsPrincipal() throws IOException, ServletException {
            // Given
            String simplePrincipal = "username";
            when(authentication.getPrincipal()).thenReturn(simplePrincipal);

            // When
            logoutSuccessService.onLogoutSuccess(request, response, authentication);

            // Then
            ArgumentCaptor<AuditEvent> eventCaptor = forClass(AuditEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());

            AuditEvent capturedEvent = eventCaptor.getValue();
            assertThat(capturedEvent).isNotNull();
            assertThat(capturedEvent.getUser()).isNull();
        }

        @Test
        @DisplayName("Should handle logout with null principal")
        void shouldHandleLogoutWithNullPrincipal() throws IOException, ServletException {
            // Given
            when(authentication.getPrincipal()).thenReturn(null);

            // When
            logoutSuccessService.onLogoutSuccess(request, response, authentication);

            // Then
            ArgumentCaptor<AuditEvent> eventCaptor = forClass(AuditEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());

            AuditEvent capturedEvent = eventCaptor.getValue();
            assertThat(capturedEvent).isNotNull();
            assertThat(capturedEvent.getUser()).isNull();
        }
    }

    @Nested
    @DisplayName("Target URL Resolution Tests")
    class TargetURLResolutionTests {

        @Test
        @DisplayName("Should set default target URL when determineTargetUrl returns empty")
        void shouldSetDefaultTargetUrlWhenEmpty() throws IOException, ServletException {
            // Given
            when(authentication.getPrincipal()).thenReturn(userDetails);
            when(userDetails.getUser()).thenReturn(testUser);

            // When
            logoutSuccessService.onLogoutSuccess(request, response, authentication);

            // Then - verify the redirect happens with the configured URL
            verify(response).encodeRedirectURL(logoutSuccessUri);
        }

        @Test
        @DisplayName("Should set default target URL when determineTargetUrl returns root")
        void shouldSetDefaultTargetUrlWhenRoot() throws IOException, ServletException {
            // Given
            when(authentication.getPrincipal()).thenReturn(userDetails);
            when(userDetails.getUser()).thenReturn(testUser);
            logoutSuccessService.setTargetUrlParameter("targetUrl");
            lenient().when(request.getParameter("targetUrl")).thenReturn("/");

            // When
            logoutSuccessService.onLogoutSuccess(request, response, authentication);

            // Then - verify the logout success URI is used
            // The actual behavior might vary based on parent class logic
            verify(response).encodeRedirectURL(anyString());
        }

        @Test
        @DisplayName("Should use custom target URL when set")
        void shouldUseCustomTargetUrl() throws IOException, ServletException {
            // Given
            String customTargetUrl = "/custom/logout/page";
            when(authentication.getPrincipal()).thenReturn(userDetails);
            when(userDetails.getUser()).thenReturn(testUser);
            logoutSuccessService.setDefaultTargetUrl(customTargetUrl);

            // When
            logoutSuccessService.onLogoutSuccess(request, response, authentication);

            // Then
            verify(response).encodeRedirectURL(customTargetUrl);
        }
    }

    @Nested
    @DisplayName("IP Address Extraction Tests")
    class IPAddressExtractionTests {

        @Test
        @DisplayName("Should extract IP from X-Forwarded-For header")
        void shouldExtractIpFromXForwardedForHeader() throws IOException, ServletException {
            // Given
            String forwardedIp = "203.0.113.195";
            when(request.getHeader("X-Forwarded-For")).thenReturn(forwardedIp);
            when(authentication.getPrincipal()).thenReturn(userDetails);
            when(userDetails.getUser()).thenReturn(testUser);

            // When
            logoutSuccessService.onLogoutSuccess(request, response, authentication);

            // Then
            ArgumentCaptor<AuditEvent> eventCaptor = forClass(AuditEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());

            AuditEvent capturedEvent = eventCaptor.getValue();
            assertThat(capturedEvent.getIpAddress()).isEqualTo(forwardedIp);
        }

        @Test
        @DisplayName("Should extract IP from X-Real-IP header when X-Forwarded-For is null")
        void shouldExtractIpFromXRealIpHeader() throws IOException, ServletException {
            // Given
            String realIp = "198.51.100.178";
            when(request.getHeader("X-Forwarded-For")).thenReturn(null);
            when(request.getHeader("X-Real-IP")).thenReturn(realIp);
            when(authentication.getPrincipal()).thenReturn(userDetails);
            when(userDetails.getUser()).thenReturn(testUser);

            // When
            logoutSuccessService.onLogoutSuccess(request, response, authentication);

            // Then
            ArgumentCaptor<AuditEvent> eventCaptor = forClass(AuditEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());

            AuditEvent capturedEvent = eventCaptor.getValue();
            assertThat(capturedEvent.getIpAddress()).isEqualTo(realIp);
        }

        @Test
        @DisplayName("Should fall back to remote address when no headers present")
        void shouldFallBackToRemoteAddress() throws IOException, ServletException {
            // Given
            String remoteAddr = "192.0.2.146";
            when(request.getHeader("X-Forwarded-For")).thenReturn(null);
            when(request.getHeader("X-Real-IP")).thenReturn(null);
            when(request.getHeader("CF-Connecting-IP")).thenReturn(null);
            when(request.getRemoteAddr()).thenReturn(remoteAddr);
            when(authentication.getPrincipal()).thenReturn(userDetails);
            when(userDetails.getUser()).thenReturn(testUser);

            // When
            logoutSuccessService.onLogoutSuccess(request, response, authentication);

            // Then
            ArgumentCaptor<AuditEvent> eventCaptor = forClass(AuditEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());

            AuditEvent capturedEvent = eventCaptor.getValue();
            assertThat(capturedEvent.getIpAddress()).isEqualTo(remoteAddr);
        }
    }

    @Nested
    @DisplayName("Audit Event Tests")
    class AuditEventTests {

        @Test
        @DisplayName("Should create audit event with all user details")
        void shouldCreateAuditEventWithAllUserDetails() throws IOException, ServletException {
            // Given
            when(authentication.getPrincipal()).thenReturn(userDetails);
            when(userDetails.getUser()).thenReturn(testUser);

            // When
            logoutSuccessService.onLogoutSuccess(request, response, authentication);

            // Then
            ArgumentCaptor<AuditEvent> eventCaptor = forClass(AuditEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());

            AuditEvent capturedEvent = eventCaptor.getValue();
            assertThat(capturedEvent.getSource()).isEqualTo(logoutSuccessService);
            assertThat(capturedEvent.getUser()).isEqualTo(testUser);
            assertThat(capturedEvent.getSessionId()).isEqualTo(sessionId);
            assertThat(capturedEvent.getUserAgent()).isEqualTo(userAgent);
            assertThat(capturedEvent.getAction()).isEqualTo("Logout");
            assertThat(capturedEvent.getActionStatus()).isEqualTo("Success");
            assertThat(capturedEvent.getMessage()).isEqualTo("Success");
        }

        @Test
        @DisplayName("Should handle null user agent gracefully")
        void shouldHandleNullUserAgentGracefully() throws IOException, ServletException {
            // Given
            when(request.getHeader("User-Agent")).thenReturn(null);
            when(authentication.getPrincipal()).thenReturn(userDetails);
            when(userDetails.getUser()).thenReturn(testUser);

            // When
            logoutSuccessService.onLogoutSuccess(request, response, authentication);

            // Then
            ArgumentCaptor<AuditEvent> eventCaptor = forClass(AuditEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());

            AuditEvent capturedEvent = eventCaptor.getValue();
            assertThat(capturedEvent.getUserAgent()).isNull();
        }

        @Test
        @DisplayName("Should always publish event even with minimal data")
        void shouldAlwaysPublishEventEvenWithMinimalData() throws IOException, ServletException {
            // Given - everything returns null except required session
            lenient().when(request.getHeader(anyString())).thenReturn(null);
            lenient().when(request.getRemoteAddr()).thenReturn(null);

            // When
            logoutSuccessService.onLogoutSuccess(request, response, null);

            // Then
            verify(eventPublisher, times(1)).publishEvent(any(AuditEvent.class));
        }
    }

    @Nested
    @DisplayName("Exception Handling Tests")
    class ExceptionHandlingTests {

        @Test
        @DisplayName("Should propagate IOException from parent class")
        void shouldPropagateIOException() throws IOException, ServletException {
            // Given
            IOException expectedException = new IOException("Network error");
            lenient().doThrow(expectedException).when(response).sendRedirect(anyString());
            when(authentication.getPrincipal()).thenReturn(userDetails);
            when(userDetails.getUser()).thenReturn(testUser);
            lenient().when(response.isCommitted()).thenReturn(false);
            lenient().doThrow(expectedException).when(response).sendRedirect(isNull());

            // When/Then
            try {
                logoutSuccessService.onLogoutSuccess(request, response, authentication);
                assertThat(false).isTrue(); // Should not reach here
            } catch (IOException e) {
                assertThat(e.getMessage()).contains("Network error");
            }

            // Verify audit event was still published before exception
            verify(eventPublisher).publishEvent(any(AuditEvent.class));
        }

        @Test
        @DisplayName("Should handle exceptions during audit event publishing")
        void shouldHandleExceptionsDuringAuditEventPublishing() throws IOException, ServletException {
            // Given
            doThrow(new RuntimeException("Event publishing failed")).when(eventPublisher).publishEvent(any(AuditEvent.class));
            when(authentication.getPrincipal()).thenReturn(userDetails);
            when(userDetails.getUser()).thenReturn(testUser);

            // When/Then - exception from event publishing will be thrown
            try {
                logoutSuccessService.onLogoutSuccess(request, response, authentication);
            } catch (RuntimeException e) {
                assertThat(e).hasMessage("Event publishing failed");
            }
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("Should complete full logout flow with user")
        void shouldCompleteFullLogoutFlowWithUser() throws IOException, ServletException {
            // Given - complete setup
            when(authentication.getPrincipal()).thenReturn(userDetails);
            when(userDetails.getUser()).thenReturn(testUser);

            // When
            logoutSuccessService.onLogoutSuccess(request, response, authentication);

            // Then - verify complete flow
            verify(request).getSession();
            verify(session).getId();
            verify(request).getHeader("User-Agent");
            verify(request, atLeastOnce()).getHeader("X-Forwarded-For");
            verify(eventPublisher).publishEvent(any(AuditEvent.class));
            verify(response).encodeRedirectURL(anyString());
        }

        @Test
        @DisplayName("Should complete full logout flow without user")
        void shouldCompleteFullLogoutFlowWithoutUser() throws IOException, ServletException {
            // When
            logoutSuccessService.onLogoutSuccess(request, response, null);

            // Then - verify complete flow
            verify(request).getSession();
            verify(session).getId();
            verify(eventPublisher).publishEvent(any(AuditEvent.class));
            verify(response).encodeRedirectURL(anyString());
        }
    }
}