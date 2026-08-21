package com.digitalsanctuary.spring.user.security;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import com.digitalsanctuary.spring.user.test.annotations.SecurityTest;
import jakarta.servlet.Filter;

/**
 * Verifies that enabling step-up in a full application context produces a working configuration.
 *
 * <p>
 * The two halves have to arrive together. The {@link StepUpService} bean enforces freshness, and factor merging on the
 * authentication processing filters is what lets a user refresh a factor at all: without it, re-asserting while logged
 * in replaces the authentication and drops every authority the {@code UserDetailsService} does not re-supply. Merging
 * was previously tied to {@code user.mfa.enabled}, so this checks that step-up alone switches it on, with MFA off.
 * </p>
 */
@SecurityTest
@TestPropertySource(properties = {"user.security.stepUp.enabled=true", "user.mfa.enabled=false", "user.webauthn.enabled=true"})
@DisplayName("Step-Up Integration Tests")
class StepUpIntegrationTest {

    @Autowired
    private FilterChainProxy filterChainProxy;

    @Autowired(required = false)
    private StepUpService stepUpService;

    @Test
    @DisplayName("should register the built-in step-up service when step-up is enabled")
    void shouldRegisterBuiltInStepUpService() {
        assertThat(stepUpService).as("user.security.stepUp.enabled=true must register a StepUpService")
                .isInstanceOf(DSFactorFreshnessStepUpService.class);
    }

    @Test
    @DisplayName("should enable factor merging on authentication filters when step-up is enabled and MFA is not")
    void shouldEnableFactorMergingForStepUpAlone() {
        List<AbstractAuthenticationProcessingFilter> processingFilters = findAuthenticationProcessingFilters();

        assertThat(processingFilters).as("the security filter chain must contain authentication processing filters").isNotEmpty();
        assertThat(processingFilters)
                .as("every authentication processing filter must have mfaEnabled=true, or re-asserting for step-up would "
                        + "replace the session's authorities instead of refreshing the factor on them")
                .allSatisfy(filter -> assertThat((Boolean) ReflectionTestUtils.getField(filter, "mfaEnabled"))
                        .as("mfaEnabled on %s", filter.getClass().getSimpleName()).isTrue());
    }

    private List<AbstractAuthenticationProcessingFilter> findAuthenticationProcessingFilters() {
        List<AbstractAuthenticationProcessingFilter> result = new ArrayList<>();
        for (SecurityFilterChain chain : filterChainProxy.getFilterChains()) {
            for (Filter filter : chain.getFilters()) {
                if (filter instanceof AbstractAuthenticationProcessingFilter processingFilter) {
                    result.add(processingFilter);
                }
            }
        }
        return result;
    }
}
