package com.digitalsanctuary.spring.user.captcha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.digitalsanctuary.spring.user.audit.AuditEvent;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
@DisplayName("CaptchaValidationInterceptor")
class CaptchaValidationInterceptorTest {

    @Mock
    private ObjectProvider<CaptchaService> captchaServiceProvider;

    @Mock
    private CaptchaService captchaService;

    @Mock
    private MessageSource messages;

    @Mock
    private ObjectProvider<ObjectMapper> objectMapperProvider;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private CaptchaConfigProperties properties;
    private CaptchaValidationInterceptor interceptor;

    @BeforeEach
    void setUp() {
        properties = new CaptchaConfigProperties();
        properties.setEnabled(true);
        interceptor = new CaptchaValidationInterceptor(properties, captchaServiceProvider, messages,
                objectMapperProvider, eventPublisher);
        // lenient(): shared happy-path stubs; pass-through tests legitimately never consume them
        // (e.g. a non-POST request touches none of these collaborators).
        lenient().when(objectMapperProvider.getIfAvailable(any())).thenReturn(objectMapper);
        lenient().when(captchaServiceProvider.getIfAvailable()).thenReturn(captchaService);
        lenient().when(messages.getMessage(eq("message.captcha.validation-failed"), isNull(), anyString(),
                any(Locale.class))).thenAnswer(invocation -> invocation.getArgument(2));
    }

    private MockHttpServletRequest postTo(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRequestURI(path);
        return request;
    }

    private void assertRejectedWithCaptchaJson(MockHttpServletResponse response) throws Exception {
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/json");
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.get("success").asBoolean()).isFalse();
        assertThat(body.get("code").asInt()).isEqualTo(CaptchaValidationInterceptor.ERROR_CODE_CAPTCHA_FAILED);
    }

    @Test
    void shouldPublishAuditEventWhenRejecting() throws Exception {
        // Rejection volume is the signal that tells an operator the protection is working, so it
        // must be auditable the same way every other API rejection is.
        MockHttpServletRequest request = postTo(CaptchaAction.REGISTRATION.path());
        request.setRemoteAddr("192.0.2.55");

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isFalse();

        // AuditEvent extends ApplicationEvent, so the call binds to publishEvent(ApplicationEvent),
        // not the publishEvent(Object) overload.
        ArgumentCaptor<ApplicationEvent> captor = ArgumentCaptor.forClass(ApplicationEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(AuditEvent.class);
        AuditEvent event = (AuditEvent) captor.getValue();
        assertThat(event.getAction()).isEqualTo("CaptchaValidation");
        assertThat(event.getActionStatus()).isEqualTo("Failure");
        assertThat(event.getIpAddress()).isEqualTo("192.0.2.55");
    }

    @Test
    void shouldNotCreateSessionWhenRejecting() throws Exception {
        // These requests are unauthenticated and frequently automated; minting a session per
        // rejection would let an abuser grow session storage just by being rejected.
        MockHttpServletRequest request = postTo(CaptchaAction.REGISTRATION.path());

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isFalse();
        assertThat(request.getSession(false)).isNull();
    }

    @Test
    void shouldStillRejectWhenAuditPublishingFails() throws Exception {
        // Auditing must never turn a rejection into a 500 — the denial matters more than the record.
        doThrow(new IllegalStateException("publisher down")).when(eventPublisher)
                .publishEvent(any(ApplicationEvent.class));
        MockHttpServletRequest request = postTo(CaptchaAction.REGISTRATION.path());
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertRejectedWithCaptchaJson(response);
    }

    @Test
    void shouldPassThroughWhenRequestIsNotPost() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", CaptchaAction.REGISTRATION.path());
        request.setRequestURI(CaptchaAction.REGISTRATION.path());

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
        verify(captchaService, never()).verify(any());
    }

    @Test
    void shouldPassThroughWhenActionToggleDisabled() throws Exception {
        properties.getProtect().setRegistration(false);

        MockHttpServletRequest request = postTo(CaptchaAction.REGISTRATION.path());

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
        verify(captchaService, never()).verify(any());
    }

    @Test
    void shouldStillProtectOtherActionsWhenOneToggleDisabled() throws Exception {
        properties.getProtect().setRegistration(false);

        MockHttpServletRequest request = postTo(CaptchaAction.RESET_PASSWORD.path());
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertRejectedWithCaptchaJson(response);
    }

    @Test
    void shouldProtectPasswordlessRegistrationSeparatelyFromRegistration() throws Exception {
        // /user/registration is an exact PathPattern and does not match the passwordless sub-path,
        // so the two need distinct actions; turning one off must not turn the other off.
        properties.getProtect().setRegistration(false);

        MockHttpServletRequest request = postTo(CaptchaAction.PASSWORDLESS_REGISTRATION.path());
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertRejectedWithCaptchaJson(response);
    }

    @Test
    void shouldPassThroughWhenPasswordlessRegistrationToggleDisabled() throws Exception {
        properties.getProtect().setPasswordlessRegistration(false);

        MockHttpServletRequest request = postTo(CaptchaAction.PASSWORDLESS_REGISTRATION.path());

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
        verify(captchaService, never()).verify(any());
    }

    @Test
    void shouldResolvePasswordlessRegistrationActionWhenTokenValid() throws Exception {
        MockHttpServletRequest request = postTo(CaptchaAction.PASSWORDLESS_REGISTRATION.path());
        request.addHeader(CaptchaValidationInterceptor.TOKEN_HEADER, "good-token");
        when(captchaService.verify(any())).thenReturn(CaptchaVerification.verified());

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();

        ArgumentCaptor<CaptchaContext> captor = ArgumentCaptor.forClass(CaptchaContext.class);
        verify(captchaService).verify(captor.capture());
        assertThat(captor.getValue().action()).isEqualTo(CaptchaAction.PASSWORDLESS_REGISTRATION);
    }

    @Test
    void shouldPassThroughWhenResendTokenToggleDisabled() throws Exception {
        properties.getProtect().setResendRegistrationToken(false);

        MockHttpServletRequest request = postTo(CaptchaAction.RESEND_REGISTRATION_TOKEN.path());

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
        verify(captchaService, never()).verify(any());
    }

    @Test
    void shouldPassThroughWhenResetPasswordToggleDisabled() throws Exception {
        properties.getProtect().setResetPassword(false);

        MockHttpServletRequest request = postTo(CaptchaAction.RESET_PASSWORD.path());

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
        verify(captchaService, never()).verify(any());
    }

    @Test
    void shouldRejectWithJsonResponseWhenTokenMissing() throws Exception {
        MockHttpServletRequest request = postTo(CaptchaAction.RESET_PASSWORD.path());
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(request, response, new Object());

        assertThat(proceed).isFalse();
        assertRejectedWithCaptchaJson(response);
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.get("messages").get(0).asText()).contains("CAPTCHA");
        verify(captchaService, never()).verify(any());
    }

    @Test
    void shouldRejectWhenTokenIsBlank() throws Exception {
        MockHttpServletRequest request = postTo(CaptchaAction.REGISTRATION.path());
        request.addHeader(CaptchaValidationInterceptor.TOKEN_HEADER, "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertRejectedWithCaptchaJson(response);
        verify(captchaService, never()).verify(any());
    }

    @Test
    void shouldFallBackToParameterWhenHeaderIsBlank() throws Exception {
        MockHttpServletRequest request = postTo(CaptchaAction.REGISTRATION.path());
        request.addHeader(CaptchaValidationInterceptor.TOKEN_HEADER, "  ");
        request.setParameter(CaptchaValidationInterceptor.TOKEN_PARAMETER, "param-token");
        when(captchaService.verify(any())).thenReturn(CaptchaVerification.verified());

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();

        ArgumentCaptor<CaptchaContext> captor = ArgumentCaptor.forClass(CaptchaContext.class);
        verify(captchaService).verify(captor.capture());
        assertThat(captor.getValue().token()).isEqualTo("param-token");
    }

    @Test
    void shouldRejectWhenProviderRejectsToken() throws Exception {
        MockHttpServletRequest request = postTo(CaptchaAction.RESEND_REGISTRATION_TOKEN.path());
        request.addHeader(CaptchaValidationInterceptor.TOKEN_HEADER, "bad-token");
        when(captchaService.verify(any())).thenReturn(CaptchaVerification.rejected("invalid"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertRejectedWithCaptchaJson(response);

        ArgumentCaptor<CaptchaContext> captor = ArgumentCaptor.forClass(CaptchaContext.class);
        verify(captchaService).verify(captor.capture());
        assertThat(captor.getValue().token()).isEqualTo("bad-token");
        assertThat(captor.getValue().action()).isEqualTo(CaptchaAction.RESEND_REGISTRATION_TOKEN);
    }

    @Test
    void shouldRejectWhenProviderReportsError() throws Exception {
        MockHttpServletRequest request = postTo(CaptchaAction.REGISTRATION.path());
        request.addHeader(CaptchaValidationInterceptor.TOKEN_HEADER, "good-token");
        when(captchaService.verify(any())).thenReturn(CaptchaVerification.error("provider unreachable"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertRejectedWithCaptchaJson(response);
    }

    @Test
    void shouldRejectWithDocumentedBodyWhenProviderThrows() throws Exception {
        // CaptchaService is a public SPI: a third-party implementation may throw. The framework,
        // not the implementation, must keep that fail-closed AND keep the documented 403 contract.
        MockHttpServletRequest request = postTo(CaptchaAction.REGISTRATION.path());
        request.addHeader(CaptchaValidationInterceptor.TOKEN_HEADER, "good-token");
        when(captchaService.verify(any())).thenThrow(new IllegalStateException("provider blew up"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertRejectedWithCaptchaJson(response);
    }

    @Test
    void shouldRejectWithDocumentedBodyWhenProviderReturnsNull() throws Exception {
        MockHttpServletRequest request = postTo(CaptchaAction.REGISTRATION.path());
        request.addHeader(CaptchaValidationInterceptor.TOKEN_HEADER, "good-token");
        when(captchaService.verify(any())).thenReturn(null);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertRejectedWithCaptchaJson(response);
    }

    @Test
    void shouldPassThroughWhenHeaderTokenValid() throws Exception {
        MockHttpServletRequest request = postTo(CaptchaAction.REGISTRATION.path());
        request.addHeader(CaptchaValidationInterceptor.TOKEN_HEADER, "good-token");
        when(captchaService.verify(any())).thenReturn(CaptchaVerification.verified());

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();

        ArgumentCaptor<CaptchaContext> captor = ArgumentCaptor.forClass(CaptchaContext.class);
        verify(captchaService).verify(captor.capture());
        assertThat(captor.getValue().action()).isEqualTo(CaptchaAction.REGISTRATION);
        assertThat(captor.getValue().token()).isEqualTo("good-token");
    }

    @Test
    void shouldFallBackToRequestParameterWhenHeaderAbsent() throws Exception {
        MockHttpServletRequest request = postTo(CaptchaAction.REGISTRATION.path());
        request.setParameter(CaptchaValidationInterceptor.TOKEN_PARAMETER, "param-token");
        when(captchaService.verify(any())).thenReturn(CaptchaVerification.verified());

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();

        ArgumentCaptor<CaptchaContext> captor = ArgumentCaptor.forClass(CaptchaContext.class);
        verify(captchaService).verify(captor.capture());
        assertThat(captor.getValue().token()).isEqualTo("param-token");
    }

    @Test
    void shouldReportForwardedClientIpWhenPresent() throws Exception {
        // The IP sent to the provider and the one in rejection logs must be the same value, so it
        // is resolved once by the interceptor rather than separately by each provider.
        MockHttpServletRequest request = postTo(CaptchaAction.REGISTRATION.path());
        request.addHeader(CaptchaValidationInterceptor.TOKEN_HEADER, "good-token");
        request.addHeader("X-Forwarded-For", "203.0.113.7, 198.51.100.1");
        when(captchaService.verify(any())).thenReturn(CaptchaVerification.verified());

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();

        ArgumentCaptor<CaptchaContext> captor = ArgumentCaptor.forClass(CaptchaContext.class);
        verify(captchaService).verify(captor.capture());
        assertThat(captor.getValue().remoteIp()).isEqualTo("203.0.113.7");
    }

    @Test
    void shouldIgnoreLiteralUnknownForwardedForValue() throws Exception {
        // Some older proxies send "unknown" instead of omitting the header; forwarding that
        // placeholder to the provider is worse than using the socket address.
        MockHttpServletRequest request = postTo(CaptchaAction.REGISTRATION.path());
        request.addHeader(CaptchaValidationInterceptor.TOKEN_HEADER, "good-token");
        request.addHeader("X-Forwarded-For", "unknown");
        request.setRemoteAddr("192.0.2.55");
        when(captchaService.verify(any())).thenReturn(CaptchaVerification.verified());

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();

        ArgumentCaptor<CaptchaContext> captor = ArgumentCaptor.forClass(CaptchaContext.class);
        verify(captchaService).verify(captor.capture());
        assertThat(captor.getValue().remoteIp()).isEqualTo("192.0.2.55");
    }

    @Test
    void shouldFallBackToRemoteAddrWhenNoForwardedHeader() throws Exception {
        MockHttpServletRequest request = postTo(CaptchaAction.REGISTRATION.path());
        request.addHeader(CaptchaValidationInterceptor.TOKEN_HEADER, "good-token");
        request.setRemoteAddr("192.0.2.55");
        when(captchaService.verify(any())).thenReturn(CaptchaVerification.verified());

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();

        ArgumentCaptor<CaptchaContext> captor = ArgumentCaptor.forClass(CaptchaContext.class);
        verify(captchaService).verify(captor.capture());
        assertThat(captor.getValue().remoteIp()).isEqualTo("192.0.2.55");
    }

    @Test
    void shouldRejectWhenCaptchaServiceUnavailable() throws Exception {
        when(captchaServiceProvider.getIfAvailable()).thenReturn(null);
        MockHttpServletRequest request = postTo(CaptchaAction.REGISTRATION.path());
        request.addHeader(CaptchaValidationInterceptor.TOKEN_HEADER, "good-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertRejectedWithCaptchaJson(response);
    }

    @Test
    void shouldWriteValidJsonWhenMessageContainsQuotes() throws Exception {
        when(messages.getMessage(eq("message.captcha.validation-failed"), isNull(), anyString(), any(Locale.class)))
                .thenReturn("Die \"CAPTCHA\"-Prüfung ist fehlgeschlagen.");
        MockHttpServletRequest request = postTo(CaptchaAction.REGISTRATION.path());
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.get("messages").get(0).asText()).isEqualTo("Die \"CAPTCHA\"-Prüfung ist fehlgeschlagen.");
    }

    @Test
    void shouldRejectWhenPathCarriesMatrixParameters() throws Exception {
        // PathPattern matching (used to register this interceptor) strips matrix parameters per
        // segment, so this request reaches preHandle; enforcement must strip them the same way.
        MockHttpServletRequest request = postTo(CaptchaAction.REGISTRATION.path() + ";jsessionid=abc");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertRejectedWithCaptchaJson(response);
        verify(captchaService, never()).verify(any());
    }

    @Test
    void shouldRejectWhenPathIsPercentEncoded() throws Exception {
        // PathPattern matching URL-decodes segments before comparing, so /user/%72egistration
        // reaches preHandle as a registration request; enforcement must decode the same way.
        MockHttpServletRequest request = postTo("/user/%72egistration");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertRejectedWithCaptchaJson(response);
        verify(captchaService, never()).verify(any());
    }

    @Test
    void shouldRejectWhenPathMatchesNoKnownAction() throws Exception {
        // Last-line defense against registration and enforcement disagreeing (e.g. a consumer
        // installing a custom PathPatternParser via PathMatchConfigurer). Unreachable through the
        // real dispatcher; must fail closed rather than wave the request through.
        MockHttpServletRequest request = postTo("/user/somethingElse");
        request.addHeader(CaptchaValidationInterceptor.TOKEN_HEADER, "good-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertRejectedWithCaptchaJson(response);
        verify(captchaService, never()).verify(any());
    }

    @Test
    void shouldHandleContextPathWhenResolvingAction() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST",
                "/app" + CaptchaAction.REGISTRATION.path());
        request.setContextPath("/app");
        request.setRequestURI("/app" + CaptchaAction.REGISTRATION.path());
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertRejectedWithCaptchaJson(response);
    }
}
