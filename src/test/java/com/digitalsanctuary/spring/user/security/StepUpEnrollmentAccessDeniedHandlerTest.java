package com.digitalsanctuary.spring.user.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import com.digitalsanctuary.spring.user.exceptions.WebAuthnStepUpRequiredException;

/**
 * Tests for {@link StepUpEnrollmentAccessDeniedHandler}, which must render only a freshness
 * ({@link AuthorizationDeniedException}) denial as the 401 step-up-required contract and delegate everything else so it
 * keeps its normal 403.
 */
@DisplayName("StepUpEnrollmentAccessDeniedHandler Tests")
class StepUpEnrollmentAccessDeniedHandlerTest {

    private AccessDeniedHandler delegate;
    private StepUpEnrollmentAccessDeniedHandler handler;

    @BeforeEach
    void setUp() {
        delegate = mock(AccessDeniedHandler.class);
        handler = new StepUpEnrollmentAccessDeniedHandler(delegate);
    }

    @Test
    @DisplayName("should render 401 step-up-required JSON when the denial is a freshness AuthorizationDeniedException")
    void shouldRenderStepUpContractWhenAuthorizationDenied() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, new AuthorizationDeniedException("Access Denied"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).contains("application/json");
        assertThat(response.getCharacterEncoding()).isEqualTo("UTF-8");
        String body = response.getContentAsString();
        assertThat(body).contains("\"error\":\"" + WebAuthnStepUpRequiredException.ERROR_CODE + "\"");
        assertThat(body).contains("Recent authentication is required to add a passkey");
        verifyNoInteractions(delegate);
    }

    @Test
    @DisplayName("should delegate and leave the response untouched when the denial is not an AuthorizationDeniedException")
    void shouldDelegateWhenNotAuthorizationDenied() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        // A CSRF failure is an AccessDeniedException but not an AuthorizationDeniedException; it must keep its 403.
        AccessDeniedException csrfDenial = new AccessDeniedException("Invalid CSRF token");

        handler.handle(request, response, csrfDenial);

        verify(delegate).handle(request, response, csrfDenial);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEmpty();
    }

    @Test
    @DisplayName("should not write the step-up body when the response is already committed")
    void shouldReturnEarlyWhenResponseCommitted() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setCommitted(true);

        handler.handle(request, response, new AuthorizationDeniedException("Access Denied"));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEmpty();
        verifyNoInteractions(delegate);
    }

    @Test
    @DisplayName("should reject a null delegate at construction rather than deferring the failure")
    void shouldRejectNullDelegate() {
        assertThatThrownBy(() -> new StepUpEnrollmentAccessDeniedHandler(null))
                .isInstanceOf(NullPointerException.class);
    }
}
