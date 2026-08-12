package com.digitalsanctuary.spring.user.captcha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;

import com.digitalsanctuary.cf.turnstile.config.TurnstileConfigProperties;
import com.digitalsanctuary.cf.turnstile.service.TurnstileValidationService;

@ExtendWith(MockitoExtension.class)
@DisplayName("TurnstileCaptchaService adapter")
class TurnstileCaptchaServiceTest {

    private static final String CLIENT_IP = "203.0.113.7";

    @Mock
    private ObjectProvider<TurnstileValidationService> turnstileServiceProvider;

    @Mock
    private TurnstileValidationService turnstileValidationService;

    @Mock
    private ObjectProvider<TurnstileConfigProperties> turnstilePropertiesProvider;

    private TurnstileCaptchaService captchaService;

    @BeforeEach
    void setUp() {
        captchaService = new TurnstileCaptchaService(turnstileServiceProvider, turnstilePropertiesProvider);
        // lenient(): only the configurationErrors tests read the properties bean; verify/siteKey
        // tests legitimately never touch it.
        lenient().when(turnstilePropertiesProvider.getIfAvailable()).thenReturn(usableProperties());
    }

    private TurnstileConfigProperties usableProperties() {
        TurnstileConfigProperties properties = new TurnstileConfigProperties();
        properties.setSecret("real-secret");
        properties.setSitekey("real-site-key");
        return properties;
    }

    private CaptchaContext contextFor(String token) {
        return new CaptchaContext(CaptchaAction.REGISTRATION, token, CLIENT_IP, new MockHttpServletRequest());
    }

    @Test
    void shouldReportVerifiedWhenTurnstileAcceptsToken() {
        when(turnstileServiceProvider.getIfAvailable()).thenReturn(turnstileValidationService);
        when(turnstileValidationService.validateTurnstileResponse("tok-123", CLIENT_IP)).thenReturn(true);

        assertThat(captchaService.verify(contextFor("tok-123")).isVerified()).isTrue();
        // The framework resolves the client IP; the adapter must forward that value rather than
        // re-deriving one, so provider calls and rejection logs name the same client.
        verify(turnstileValidationService).validateTurnstileResponse("tok-123", CLIENT_IP);
    }

    @Test
    void shouldReportRejectedWhenTurnstileRejectsToken() {
        when(turnstileServiceProvider.getIfAvailable()).thenReturn(turnstileValidationService);
        when(turnstileValidationService.validateTurnstileResponse("bad-token", CLIENT_IP)).thenReturn(false);

        CaptchaVerification result = captchaService.verify(contextFor("bad-token"));

        assertThat(result.isVerified()).isFalse();
        assertThat(result.outcome()).isEqualTo(CaptchaVerification.Outcome.REJECTED);
    }

    @Test
    void shouldReportErrorWhenTurnstileServiceBeanMissing() {
        when(turnstileServiceProvider.getIfAvailable()).thenReturn(null);

        CaptchaVerification result = captchaService.verify(contextFor("tok-123"));

        assertThat(result.isVerified()).isFalse();
        assertThat(result.outcome()).isEqualTo(CaptchaVerification.Outcome.ERROR);
    }

    @Test
    void shouldReportErrorWhenValidateTurnstileResponseThrows() {
        when(turnstileServiceProvider.getIfAvailable()).thenReturn(turnstileValidationService);
        when(turnstileValidationService.validateTurnstileResponse("tok-123", CLIENT_IP))
                .thenThrow(new RuntimeException("Validation service unavailable"));

        CaptchaVerification result = captchaService.verify(contextFor("tok-123"));

        assertThat(result.isVerified()).isFalse();
        assertThat(result.outcome()).isEqualTo(CaptchaVerification.Outcome.ERROR);
    }

    @Test
    void shouldExposeSitekeyFromTurnstileService() {
        when(turnstileServiceProvider.getIfAvailable()).thenReturn(turnstileValidationService);
        when(turnstileValidationService.getTurnstileSitekey()).thenReturn("real-site-key");

        assertThat(captchaService.siteKey()).contains("real-site-key");
    }

    @Test
    void shouldWarnWhenCloudflareTestCredentialsConfigured() {
        when(turnstileServiceProvider.getIfAvailable()).thenReturn(turnstileValidationService);
        when(turnstileValidationService.isUsingTestCredentials()).thenReturn(true);

        List<String> warnings = captchaService.configurationWarnings();

        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0)).contains("test");
    }

    @Test
    void shouldReportErrorWhenTurnstileServiceBeanMissingAtStartup() {
        when(turnstileServiceProvider.getIfAvailable()).thenReturn(null);

        assertThat(captchaService.configurationErrors())
                .anySatisfy(error -> assertThat(error).contains("TurnstileValidationService"));
    }

    @Test
    void shouldReportErrorWhenSecretMissing() {
        when(turnstileServiceProvider.getIfAvailable()).thenReturn(turnstileValidationService);
        when(turnstileValidationService.getTurnstileSitekey()).thenReturn("real-site-key");
        TurnstileConfigProperties noSecret = new TurnstileConfigProperties();
        noSecret.setSitekey("real-site-key");
        when(turnstilePropertiesProvider.getIfAvailable()).thenReturn(noSecret);

        assertThat(captchaService.configurationErrors())
                .anySatisfy(error -> assertThat(error).contains("secret"));
    }

    @Test
    void shouldReportErrorWhenSiteKeyMissing() {
        when(turnstileServiceProvider.getIfAvailable()).thenReturn(turnstileValidationService);
        when(turnstileValidationService.getTurnstileSitekey()).thenReturn("  ");

        assertThat(captchaService.configurationErrors())
                .anySatisfy(error -> assertThat(error).contains("site key"));
    }

    @Test
    void shouldWarnRatherThanErrorWhenPropertiesBeanAbsent() {
        // A consumer who excludes the Turnstile auto-configuration and hand-wires a working
        // TurnstileValidationService has no properties bean. We cannot verify their secret, but
        // that is not evidence it is broken, so this must not fail startup.
        when(turnstileServiceProvider.getIfAvailable()).thenReturn(turnstileValidationService);
        when(turnstileValidationService.getTurnstileSitekey()).thenReturn("real-site-key");
        when(turnstileValidationService.isUsingTestCredentials()).thenReturn(false);
        when(turnstilePropertiesProvider.getIfAvailable()).thenReturn(null);

        assertThat(captchaService.configurationErrors()).isEmpty();
        assertThat(captchaService.configurationWarnings())
                .anySatisfy(warning -> assertThat(warning).contains("could not be verified"));
    }

    @Test
    void shouldReportNoErrorsWhenFullyConfigured() {
        when(turnstileServiceProvider.getIfAvailable()).thenReturn(turnstileValidationService);
        when(turnstileValidationService.getTurnstileSitekey()).thenReturn("real-site-key");

        assertThat(captchaService.configurationErrors()).isEmpty();
    }

    @Test
    void shouldReturnNoWarningsWhenRealCredentialsConfigured() {
        when(turnstileServiceProvider.getIfAvailable()).thenReturn(turnstileValidationService);
        when(turnstileValidationService.isUsingTestCredentials()).thenReturn(false);

        assertThat(captchaService.configurationWarnings()).isEmpty();
    }

    @Test
    void shouldReturnEmptySiteKeyWhenTurnstileServiceBeanMissing() {
        when(turnstileServiceProvider.getIfAvailable()).thenReturn(null);

        assertThat(captchaService.siteKey()).isEmpty();
    }

    @Test
    void shouldReturnEmptySiteKeyWhenGetTurnstileSitekeyThrows() {
        when(turnstileServiceProvider.getIfAvailable()).thenReturn(turnstileValidationService);
        when(turnstileValidationService.getTurnstileSitekey())
                .thenThrow(new RuntimeException("Could not fetch site key"));

        assertThat(captchaService.siteKey()).isEmpty();
    }

    @Test
    void shouldWarnWhenIsUsingTestCredentialsThrows() {
        when(turnstileServiceProvider.getIfAvailable()).thenReturn(turnstileValidationService);
        when(turnstileValidationService.isUsingTestCredentials())
                .thenThrow(new RuntimeException("Could not query credentials"));

        List<String> warnings = captchaService.configurationWarnings();

        assertThat(warnings).isNotEmpty();
        assertThat(warnings.get(0)).contains("could not be queried");
    }
}
