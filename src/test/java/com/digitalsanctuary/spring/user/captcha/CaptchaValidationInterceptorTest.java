package com.digitalsanctuary.spring.user.captcha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.MessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CaptchaValidationInterceptor")
class CaptchaValidationInterceptorTest {

    @Mock
    private ObjectProvider<CaptchaService> captchaServiceProvider;

    @Mock
    private CaptchaService captchaService;

    @Mock
    private MessageSource messages;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private CaptchaConfigProperties properties;
    private CaptchaValidationInterceptor interceptor;

    @BeforeEach
    void setUp() {
        properties = new CaptchaConfigProperties();
        properties.setEnabled(true);
        interceptor = new CaptchaValidationInterceptor(properties, captchaServiceProvider, messages);
        when(captchaServiceProvider.getIfAvailable()).thenReturn(captchaService);
        when(messages.getMessage(eq("message.captcha.validation-failed"), isNull(), anyString(), any(Locale.class)))
                .thenAnswer(invocation -> invocation.getArgument(2));
    }

    private MockHttpServletRequest postTo(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRequestURI(path);
        return request;
    }

    @Test
    void shouldPassThroughWhenRequestIsNotPost() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET",
                CaptchaValidationInterceptor.REGISTRATION_PATH);
        request.setRequestURI(CaptchaValidationInterceptor.REGISTRATION_PATH);

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
        verify(captchaService, never()).verify(anyString(), any());
    }

    @Test
    void shouldPassThroughWhenActionToggleDisabled() throws Exception {
        properties.getProtect().setRegistration(false);

        MockHttpServletRequest request = postTo(CaptchaValidationInterceptor.REGISTRATION_PATH);

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
        verify(captchaService, never()).verify(anyString(), any());
    }

    @Test
    void shouldRejectWithJsonResponseWhenTokenMissing() throws Exception {
        MockHttpServletRequest request = postTo(CaptchaValidationInterceptor.RESET_PASSWORD_PATH);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(request, response, new Object());

        assertThat(proceed).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/json");
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.get("success").asBoolean()).isFalse();
        assertThat(body.get("code").asInt()).isEqualTo(CaptchaValidationInterceptor.ERROR_CODE_CAPTCHA_FAILED);
        assertThat(body.get("messages").get(0).asText()).contains("CAPTCHA");
        verify(captchaService, never()).verify(anyString(), any());
    }

    @Test
    void shouldRejectWhenTokenInvalid() throws Exception {
        MockHttpServletRequest request = postTo(CaptchaValidationInterceptor.RESEND_TOKEN_PATH);
        request.addHeader(CaptchaValidationInterceptor.TOKEN_HEADER, "bad-token");
        when(captchaService.verify(eq("bad-token"), any())).thenReturn(false);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void shouldPassThroughWhenHeaderTokenValid() throws Exception {
        MockHttpServletRequest request = postTo(CaptchaValidationInterceptor.REGISTRATION_PATH);
        request.addHeader(CaptchaValidationInterceptor.TOKEN_HEADER, "good-token");
        when(captchaService.verify(eq("good-token"), any())).thenReturn(true);

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
    }

    @Test
    void shouldFallBackToRequestParameterWhenHeaderAbsent() throws Exception {
        MockHttpServletRequest request = postTo(CaptchaValidationInterceptor.REGISTRATION_PATH);
        request.setParameter(CaptchaValidationInterceptor.TOKEN_PARAMETER, "param-token");
        when(captchaService.verify(eq("param-token"), any())).thenReturn(true);

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
        verify(captchaService).verify(eq("param-token"), any());
    }

    @Test
    void shouldRejectWhenCaptchaServiceUnavailable() throws Exception {
        when(captchaServiceProvider.getIfAvailable()).thenReturn(null);
        MockHttpServletRequest request = postTo(CaptchaValidationInterceptor.REGISTRATION_PATH);
        request.addHeader(CaptchaValidationInterceptor.TOKEN_HEADER, "good-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void shouldWriteValidJsonWhenMessageContainsQuotes() throws Exception {
        when(messages.getMessage(eq("message.captcha.validation-failed"), isNull(), anyString(), any(Locale.class)))
                .thenReturn("Die \"CAPTCHA\"-Prüfung ist fehlgeschlagen.");
        MockHttpServletRequest request = postTo(CaptchaValidationInterceptor.REGISTRATION_PATH);
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.get("messages").get(0).asText()).isEqualTo("Die \"CAPTCHA\"-Prüfung ist fehlgeschlagen.");
    }

    @Test
    void shouldRejectWhenPathCarriesMatrixParameters() throws Exception {
        // PathPattern matching (used to register this interceptor) strips matrix parameters per
        // segment, so this request reaches preHandle; enforcement must strip them the same way.
        MockHttpServletRequest request = postTo(CaptchaValidationInterceptor.REGISTRATION_PATH + ";jsessionid=abc");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        verify(captchaService, never()).verify(anyString(), any());
    }

    @Test
    void shouldRejectWhenPathIsPercentEncoded() throws Exception {
        // PathPattern matching URL-decodes segments before comparing, so /user/%72egistration
        // reaches preHandle as a registration request; enforcement must decode the same way.
        MockHttpServletRequest request = postTo("/user/%72egistration");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        verify(captchaService, never()).verify(anyString(), any());
    }

    @Test
    void shouldHandleContextPathWhenResolvingAction() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST",
                "/app" + CaptchaValidationInterceptor.REGISTRATION_PATH);
        request.setContextPath("/app");
        request.setRequestURI("/app" + CaptchaValidationInterceptor.REGISTRATION_PATH);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
    }
}
