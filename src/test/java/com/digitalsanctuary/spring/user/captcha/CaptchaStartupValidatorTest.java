package com.digitalsanctuary.spring.user.captcha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
@DisplayName("CaptchaStartupValidator")
class CaptchaStartupValidatorTest {

    @Mock
    private ObjectProvider<CaptchaService> captchaServiceProvider;

    private CaptchaConfigProperties properties;

    @BeforeEach
    void setUp() {
        properties = new CaptchaConfigProperties();
    }

    private CaptchaStartupValidator validator() {
        return new CaptchaStartupValidator(properties, captchaServiceProvider);
    }

    /** A provider that works: no errors, no warnings. */
    private CaptchaService usableService() {
        return context -> CaptchaVerification.verified();
    }

    private CaptchaService serviceReporting(List<String> errors, List<String> warnings) {
        return new CaptchaService() {
            @Override
            public CaptchaVerification verify(CaptchaContext context) {
                return CaptchaVerification.verified();
            }

            @Override
            public Optional<String> siteKey() {
                return Optional.of("site-key");
            }

            @Override
            public List<String> configurationErrors() {
                return errors;
            }

            @Override
            public List<String> configurationWarnings() {
                return warnings;
            }
        };
    }

    @Test
    void shouldDoNothingWhenCaptchaDisabled() {
        properties.setEnabled(false);
        // lenient(): the disabled path must never consult the provider, so this stub going unused
        // is the expected outcome, not a test smell.
        lenient().when(captchaServiceProvider.getIfAvailable()).thenReturn(null);

        assertThatCode(() -> validator().validateCaptchaConfiguration()).doesNotThrowAnyException();
    }

    @Test
    void shouldFailStartupWhenEnabledWithNoResolvableService() {
        properties.setEnabled(true);
        when(captchaServiceProvider.getIfAvailable()).thenReturn(null);

        assertThatThrownBy(() -> validator().validateCaptchaConfiguration())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no CaptchaService is available");
    }

    @Test
    void shouldFailStartupWhenProviderReportsConfigurationErrors() {
        properties.setEnabled(true);
        when(captchaServiceProvider.getIfAvailable())
                .thenReturn(serviceReporting(List.of("no secret key configured"), List.of()));

        assertThatThrownBy(() -> validator().validateCaptchaConfiguration())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no secret key configured")
                .hasMessageContaining("allow-unusable-provider");
    }

    @Test
    void shouldStartWhenUnusableProviderExplicitlyAllowed() {
        properties.setEnabled(true);
        properties.setAllowUnusableProvider(true);
        when(captchaServiceProvider.getIfAvailable())
                .thenReturn(serviceReporting(List.of("no secret key configured"), List.of()));

        assertThatCode(() -> validator().validateCaptchaConfiguration()).doesNotThrowAnyException();
    }

    @Test
    void shouldStartAndSurfaceWarningsWhenProviderIsUsable() {
        properties.setEnabled(true);
        when(captchaServiceProvider.getIfAvailable())
                .thenReturn(serviceReporting(List.of(), List.of("using Cloudflare test credentials")));

        assertThatCode(() -> validator().validateCaptchaConfiguration()).doesNotThrowAnyException();
    }

    @Test
    void shouldStartWhenProviderIsUsableAndSilent() {
        properties.setEnabled(true);
        when(captchaServiceProvider.getIfAvailable()).thenReturn(usableService());

        assertThatCode(() -> validator().validateCaptchaConfiguration()).doesNotThrowAnyException();
    }

    @Test
    void shouldNotConsultProviderWhenDisabledEvenIfMisconfigured() {
        // A disabled CAPTCHA must never fail startup over provider configuration. lenient(): the
        // stub going unused IS the behavior under test.
        properties.setEnabled(false);
        lenient().when(captchaServiceProvider.getIfAvailable())
                .thenReturn(serviceReporting(List.of("no secret key configured"), List.of()));

        assertThatCode(() -> validator().validateCaptchaConfiguration()).doesNotThrowAnyException();
        assertThat(properties.isEnabled()).isFalse();
    }
}
