package com.digitalsanctuary.spring.user.security;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import com.digitalsanctuary.spring.user.persistence.model.User;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Tests for {@link DSFactorFreshnessStepUpService}.
 */
@DisplayName("DSFactorFreshnessStepUpService Tests")
class DSFactorFreshnessStepUpServiceTest {

    private static final String EMAIL = "passkey-user@test.com";
    private static final String ACTION = "set-password";

    private final HttpServletRequest request = new MockHttpServletRequest();

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setEmail(EMAIL);
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("Freshness")
    class FreshnessTests {

        @Test
        @DisplayName("should satisfy step-up when the required factor was issued inside the window")
        void shouldSatisfyWhenFactorIsFresh() {
            authenticate(EMAIL, webAuthnFactor(Instant.now()));

            assertThat(service(120, "WEBAUTHN").isStepUpSatisfied(user, ACTION, request)).isTrue();
        }

        @Test
        @DisplayName("should deny step-up when the required factor was issued before the window")
        void shouldDenyWhenFactorIsStale() {
            authenticate(EMAIL, webAuthnFactor(Instant.now().minusSeconds(121)));

            assertThat(service(120, "WEBAUTHN").isStepUpSatisfied(user, ACTION, request)).isFalse();
        }

        @Test
        @DisplayName("should deny step-up when the session carries no factor of the required kind")
        void shouldDenyWhenFactorIsAbsent() {
            authenticate(EMAIL, new SimpleGrantedAuthority("ROLE_USER"),
                    FactorGrantedAuthority.withAuthority(FactorGrantedAuthority.PASSWORD_AUTHORITY).issuedAt(Instant.now()).build());

            assertThat(service(120, "WEBAUTHN").isStepUpSatisfied(user, ACTION, request)).isFalse();
        }

        @Test
        @DisplayName("should honor the configured TTL rather than a fixed one")
        void shouldHonorConfiguredTtl() {
            authenticate(EMAIL, webAuthnFactor(Instant.now().minusSeconds(200)));

            assertThat(service(120, "WEBAUTHN").isStepUpSatisfied(user, ACTION, request)).as("200s old against a 120s TTL").isFalse();
            assertThat(service(600, "WEBAUTHN").isStepUpSatisfied(user, ACTION, request)).as("200s old against a 600s TTL").isTrue();
        }
    }

    @Nested
    @DisplayName("Configured factors")
    class FactorSelectionTests {

        @Test
        @DisplayName("should accept any one of the configured factors")
        void shouldAcceptAnyConfiguredFactor() {
            authenticate(EMAIL,
                    FactorGrantedAuthority.withAuthority(FactorGrantedAuthority.PASSWORD_AUTHORITY).issuedAt(Instant.now()).build());

            assertThat(service(120, "WEBAUTHN", "PASSWORD").isStepUpSatisfied(user, ACTION, request))
                    .as("a fresh PASSWORD factor satisfies a WEBAUTHN-or-PASSWORD configuration").isTrue();
            assertThat(service(120, "WEBAUTHN").isStepUpSatisfied(user, ACTION, request))
                    .as("but not a WEBAUTHN-only configuration").isFalse();
        }
    }

    @Nested
    @DisplayName("Caller identity")
    class CallerIdentityTests {

        @Test
        @DisplayName("should deny step-up when there is no authentication in the context")
        void shouldDenyWhenUnauthenticated() {
            assertThat(service(120, "WEBAUTHN").isStepUpSatisfied(user, ACTION, request)).isFalse();
        }

        @Test
        @DisplayName("should deny step-up when the authentication is not authenticated")
        void shouldDenyWhenAuthenticationIsNotAuthenticated() {
            TestingAuthenticationToken token = new TestingAuthenticationToken(EMAIL, "n/a", List.of(webAuthnFactor(Instant.now())));
            token.setAuthenticated(false);
            SecurityContextHolder.getContext().setAuthentication(token);

            assertThat(service(120, "WEBAUTHN").isStepUpSatisfied(user, ACTION, request)).isFalse();
        }

        @Test
        @DisplayName("should deny step-up when the target user is not the authenticated principal")
        void shouldDenyWhenTargetUserDiffers() {
            // Someone else's fresh assertion must not authorize an operation on this user's credentials.
            authenticate("someone-else@test.com", webAuthnFactor(Instant.now()));

            assertThat(service(120, "WEBAUTHN").isStepUpSatisfied(user, ACTION, request)).isFalse();
        }

        @Test
        @DisplayName("should match the authenticated principal case-insensitively")
        void shouldMatchPrincipalCaseInsensitively() {
            authenticate(EMAIL.toUpperCase(), webAuthnFactor(Instant.now()));

            assertThat(service(120, "WEBAUTHN").isStepUpSatisfied(user, ACTION, request)).isTrue();
        }

        @Test
        @DisplayName("should deny step-up when no user is supplied")
        void shouldDenyWhenUserIsNull() {
            authenticate(EMAIL, webAuthnFactor(Instant.now()));

            assertThat(service(120, "WEBAUTHN").isStepUpSatisfied(null, ACTION, request)).isFalse();
        }
    }

    @Nested
    @DisplayName("Satisfiability")
    class SatisfiabilityTests {

        @Test
        @DisplayName("should report step-up unsatisfiable when WEBAUTHN is required and the user has no passkey")
        void shouldReportUnsatisfiableWhenUserHasNoPasskey() {
            assertThat(service(false, 120, "WEBAUTHN").canSatisfyStepUp(user)).isFalse();
        }

        @Test
        @DisplayName("should report step-up satisfiable when WEBAUTHN is required and the user has a passkey")
        void shouldReportSatisfiableWhenUserHasPasskey() {
            assertThat(service(true, 120, "WEBAUTHN").canSatisfyStepUp(user)).isTrue();
        }

        @Test
        @DisplayName("should report step-up satisfiable when the user has a password and PASSWORD is configured")
        void shouldReportSatisfiableWhenUserHasPasswordAndPasswordFactorConfigured() {
            user.setPassword("$2a$04$encoded");

            assertThat(service(false, 120, "PASSWORD").canSatisfyStepUp(user)).isTrue();
        }

        @Test
        @DisplayName("should report step-up unsatisfiable when PASSWORD is configured and the account is passwordless")
        void shouldReportUnsatisfiableWhenPasswordlessAndPasswordFactorConfigured() {
            assertThat(service(false, 120, "PASSWORD").canSatisfyStepUp(user)).isFalse();
        }

        @Test
        @DisplayName("should report step-up satisfiable when any one configured factor is achievable")
        void shouldReportSatisfiableWhenAnyConfiguredFactorIsAchievable() {
            user.setPassword("$2a$04$encoded");

            assertThat(service(false, 120, "WEBAUTHN", "PASSWORD").canSatisfyStepUp(user)).isTrue();
        }

        @Test
        @DisplayName("should report step-up satisfiable for factors whose achievability cannot be determined")
        void shouldReportSatisfiableForUndeterminableFactors() {
            // OTT is delivered out of band, so the framework cannot rule it out. Assume achievable and keep gating.
            assertThat(service(false, 120, "OTT").canSatisfyStepUp(user)).isTrue();
        }
    }

    private static DSFactorFreshnessStepUpService service(int ttlSeconds, String... factors) {
        return service(true, ttlSeconds, factors);
    }

    private static DSFactorFreshnessStepUpService service(boolean userHasPasskey, int ttlSeconds, String... factors) {
        StepUpConfigProperties config = new StepUpConfigProperties();
        config.setEnabled(true);
        config.setTtlSeconds(ttlSeconds);
        config.setFactors(List.of(factors));
        return new DSFactorFreshnessStepUpService(config, u -> userHasPasskey);
    }

    private static void authenticate(String name, GrantedAuthority... authorities) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(name, "n/a", List.of(authorities)));
    }

    private static FactorGrantedAuthority webAuthnFactor(Instant issuedAt) {
        return FactorGrantedAuthority.withAuthority(FactorGrantedAuthority.WEBAUTHN_AUTHORITY).issuedAt(issuedAt).build();
    }
}
