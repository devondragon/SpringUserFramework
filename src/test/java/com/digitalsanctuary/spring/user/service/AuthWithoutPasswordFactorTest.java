package com.digitalsanctuary.spring.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The framework logs users in without a password on three paths: the email-verification link, auto-login straight
 * after registration, and dev login. None of them runs a Spring Security {@code AuthenticationProvider}, so none of
 * them stamps a {@link FactorGrantedAuthority}, and a session with no factor cannot satisfy any freshness check.
 * <p>
 * That matters for the passkey-enrollment gate: without a stamp, a user who has just registered and verified their
 * email could never add their first passkey until they logged out and back in. Each path therefore names the factor
 * it genuinely represents.
 * </p>
 */
@DisplayName("Auth Without Password Factor Tests")
class AuthWithoutPasswordFactorTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("Factor selection")
    class FactorSelectionTests {

        @Test
        @DisplayName("should describe the email-verification link as a one-time token")
        void shouldDescribeVerificationLinkAsOneTimeToken() {
            assertThat(AuthWithoutPasswordFactor.EMAIL_VERIFICATION.getAuthority())
                    .isEqualTo(FactorGrantedAuthority.OTT_AUTHORITY);
        }

        @Test
        @DisplayName("should describe post-registration auto-login as a password login")
        void shouldDescribePostRegistrationAsPasswordLogin() {
            // The user submitted their password in the very request that triggers this login.
            assertThat(AuthWithoutPasswordFactor.REGISTRATION.getAuthority())
                    .isEqualTo(FactorGrantedAuthority.PASSWORD_AUTHORITY);
        }

        @Test
        @DisplayName("should stamp no factor for dev login")
        void shouldStampNoFactorForDevLogin() {
            // Dev login proves nothing about presence; it is a local-profile impersonation tool.
            assertThat(AuthWithoutPasswordFactor.DEV_LOGIN.getAuthority()).isNull();
        }
    }

    @Nested
    @DisplayName("Stamping")
    class StampingTests {

        @Test
        @DisplayName("should add a freshly issued factor to the authenticated authorities")
        void shouldAddFreshlyIssuedFactor() {
            Instant before = Instant.now().minusSeconds(1);

            List<GrantedAuthority> result = AuthWithoutPasswordFactor.EMAIL_VERIFICATION
                    .withFactor(List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")));

            assertThat(result).extracting(GrantedAuthority::getAuthority)
                    .contains("ROLE_USER", FactorGrantedAuthority.OTT_AUTHORITY);
            FactorGrantedAuthority factor = result.stream().filter(FactorGrantedAuthority.class::isInstance)
                    .map(FactorGrantedAuthority.class::cast).findFirst().orElseThrow();
            assertThat(factor.getIssuedAt()).isAfter(before);
        }

        @Test
        @DisplayName("should leave the authorities untouched for dev login")
        void shouldLeaveAuthoritiesUntouchedForDevLogin() {
            List<GrantedAuthority> original =
                    List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"));

            assertThat(AuthWithoutPasswordFactor.DEV_LOGIN.withFactor(original))
                    .containsExactlyElementsOf(original);
        }
    }

    private static Authentication currentAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }
}
