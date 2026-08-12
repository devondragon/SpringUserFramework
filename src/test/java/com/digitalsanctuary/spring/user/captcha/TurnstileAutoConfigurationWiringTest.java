package com.digitalsanctuary.spring.user.captcha;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import com.digitalsanctuary.cf.turnstile.TurnstileConfiguration;

/**
 * Wires the captcha auto-configuration against the <em>real</em> ds-spring-cf-turnstile
 * auto-configuration instead of a mocked {@code TurnstileValidationService}.
 *
 * <p>
 * Every other captcha test excludes {@link TurnstileConfiguration} (see
 * {@code application-test.properties}) and stubs or mocks the Turnstile beans, so a bean-name or
 * wiring change in a future library release would compile cleanly and pass the whole suite while
 * breaking every real consumer. This test pins the actual bean graph: the library's
 * auto-configuration produces the beans the adapter resolves via {@code ObjectProvider}. Cloudflare's
 * documented always-pass test credentials are used purely as configuration values — no network call
 * happens because {@code verify} is never invoked.
 * </p>
 */
@DisplayName("Captcha wiring against the real Turnstile auto-configuration")
class TurnstileAutoConfigurationWiringTest {

    /** Cloudflare's documented always-pass test site key. */
    private static final String TEST_SITE_KEY = "1x00000000000000000000AA";

    /** Cloudflare's documented always-pass test secret key. */
    private static final String TEST_SECRET_KEY = "1x0000000000000000000000000000000AA";

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TurnstileConfiguration.class, CaptchaAutoConfiguration.class))
            .withPropertyValues("user.security.captcha.enabled=true",
                    "ds.cf.turnstile.sitekey=" + TEST_SITE_KEY,
                    "ds.cf.turnstile.secret=" + TEST_SECRET_KEY);

    @Test
    void shouldResolveTurnstileAdapterAgainstRealLibraryBeansWhenEnabled() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(CaptchaService.class);
            assertThat(context.getBean(CaptchaService.class)).isInstanceOf(TurnstileCaptchaService.class);
            assertThat(context).hasSingleBean(CaptchaValidationInterceptor.class);
        });
    }

    @Test
    void shouldReportNoConfigurationErrorsWhenRealLibraryFullyConfigured() {
        contextRunner.run(context -> {
            CaptchaService captchaService = context.getBean(CaptchaService.class);
            assertThat(captchaService.configurationErrors()).isEmpty();
            assertThat(captchaService.siteKey()).contains(TEST_SITE_KEY);
        });
    }

    @Test
    void shouldSurfaceTestCredentialWarningThroughRealLibraryWhenTestKeysConfigured() {
        contextRunner.run(context -> {
            assertThat(context.getBean(CaptchaService.class).configurationWarnings())
                    .anySatisfy(warning -> assertThat(warning).contains("test"));
        });
    }

    @Test
    void shouldReportConfigurationErrorThroughRealLibraryWhenSecretMissing() {
        new WebApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(TurnstileConfiguration.class, CaptchaAutoConfiguration.class))
                .withPropertyValues("user.security.captcha.enabled=true",
                        "user.security.captcha.allow-unusable-provider=true",
                        "ds.cf.turnstile.sitekey=" + TEST_SITE_KEY)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(CaptchaService.class).configurationErrors())
                            .anySatisfy(error -> assertThat(error).contains("secret"));
                });
    }
}
