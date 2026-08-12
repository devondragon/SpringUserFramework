package com.digitalsanctuary.spring.user.captcha;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.digitalsanctuary.spring.user.test.app.TestApplication;

/**
 * Guards the shared test-scope exclusion of the Turnstile auto-configuration.
 *
 * <p>
 * {@code ds-spring-cf-turnstile} is on the test classpath, so without the exclusion in
 * {@code application-test.properties} its auto-configuration registers a login filter for every
 * test in the suite. That property is a whole-value assignment, so any future test that sets its
 * own {@code spring.autoconfigure.exclude} silently replaces it. This test fails loudly if that
 * happens, instead of leaving unrelated tests to fail in confusing ways.
 * </p>
 */
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
@DisplayName("Turnstile auto-configuration exclusion")
class TurnstileAutoConfigurationExclusionTest {

    @Value("${spring.autoconfigure.exclude:}")
    private String excluded;

    @Test
    void shouldExcludeTurnstileAutoConfigurationInTestScope() {
        assertThat(Arrays.stream(excluded.split(",")).map(String::trim))
                .contains("com.digitalsanctuary.cf.turnstile.TurnstileConfiguration");
    }
}
