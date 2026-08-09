package com.digitalsanctuary.spring.user.captcha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;

import com.digitalsanctuary.cf.turnstile.service.TurnstileValidationService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TurnstileCaptchaService adapter")
class TurnstileCaptchaServiceTest {

	@Mock
	private ObjectProvider<TurnstileValidationService> turnstileServiceProvider;

	@Mock
	private TurnstileValidationService turnstileValidationService;

	private TurnstileCaptchaService captchaService;

	@BeforeEach
	void setUp() {
		captchaService = new TurnstileCaptchaService(turnstileServiceProvider);
	}

	@Test
	void shouldDelegateToTurnstileWithClientIpWhenServiceAvailable() {
		when(turnstileServiceProvider.getIfAvailable()).thenReturn(turnstileValidationService);
		MockHttpServletRequest request = new MockHttpServletRequest();
		when(turnstileValidationService.getClientIpAddress(request)).thenReturn("203.0.113.7");
		when(turnstileValidationService.validateTurnstileResponse("tok-123", "203.0.113.7")).thenReturn(true);

		boolean result = captchaService.verify("tok-123", request);

		assertThat(result).isTrue();
		verify(turnstileValidationService).validateTurnstileResponse("tok-123", "203.0.113.7");
	}

	@Test
	void shouldFailClosedWhenTurnstileValidationFails() {
		when(turnstileServiceProvider.getIfAvailable()).thenReturn(turnstileValidationService);
		MockHttpServletRequest request = new MockHttpServletRequest();
		when(turnstileValidationService.getClientIpAddress(request)).thenReturn("203.0.113.7");
		when(turnstileValidationService.validateTurnstileResponse("bad-token", "203.0.113.7")).thenReturn(false);

		assertThat(captchaService.verify("bad-token", request)).isFalse();
	}

	@Test
	void shouldFailClosedWhenTurnstileServiceBeanMissing() {
		when(turnstileServiceProvider.getIfAvailable()).thenReturn(null);

		assertThat(captchaService.verify("tok-123", new MockHttpServletRequest())).isFalse();
	}

	@Test
	void shouldExposeSitekeyFromTurnstileService() {
		when(turnstileServiceProvider.getIfAvailable()).thenReturn(turnstileValidationService);
		when(turnstileValidationService.getTurnstileSitekey()).thenReturn("real-site-key");

		assertThat(captchaService.getSiteKey()).isEqualTo("real-site-key");
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
	void shouldWarnWhenTurnstileServiceBeanMissing() {
		when(turnstileServiceProvider.getIfAvailable()).thenReturn(null);

		assertThat(captchaService.configurationWarnings())
				.anySatisfy(warning -> assertThat(warning).contains("fail closed"));
	}

	@Test
	void shouldReturnNoWarningsWhenRealCredentialsConfigured() {
		when(turnstileServiceProvider.getIfAvailable()).thenReturn(turnstileValidationService);
		when(turnstileValidationService.isUsingTestCredentials()).thenReturn(false);

		assertThat(captchaService.configurationWarnings()).isEmpty();
	}

	@Test
	void shouldReturnNullSiteKeyWhenTurnstileServiceBeanMissing() {
		when(turnstileServiceProvider.getIfAvailable()).thenReturn(null);

		assertThat(captchaService.getSiteKey()).isNull();
	}
}
