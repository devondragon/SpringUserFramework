package com.digitalsanctuary.spring.user.security;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Guards passkey enrollment behind a recent authentication of any kind, the way GitHub's sudo mode guards adding a
 * security key.
 * <p>
 * Any factor counts, not the {@code user.security.stepUp.factors} list. With the default {@code [WEBAUTHN]} a
 * user-configured list would demand a passkey to register a first passkey, which nobody could ever satisfy.
 * </p>
 */
@DisplayName("Fresh Factor Authorization Manager Tests")
class FreshFactorAuthorizationManagerTest {

    private static final Instant NOW = Instant.parse("2026-08-20T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static FreshFactorAuthorizationManager<Object> manager() {
        return new FreshFactorAuthorizationManager<>(Duration.ofSeconds(600), CLOCK);
    }

    private static Supplier<Authentication> auth(GrantedAuthority... authorities) {
        return () -> new TestingAuthenticationToken("user@test.com", "n/a", List.of(authorities));
    }

    private static FactorGrantedAuthority factor(String authority, Instant issuedAt) {
        return FactorGrantedAuthority.withAuthority(authority).issuedAt(issuedAt).build();
    }

    private static boolean granted(AuthorizationResult result) {
        return result != null && result.isGranted();
    }

    @Nested
    @DisplayName("Freshness")
    class FreshnessTests {

        @Test
        @DisplayName("should grant when a factor was issued inside the window")
        void shouldGrantWhenFactorIsFresh() {
            assertThat(granted(manager().authorize(
                    auth(factor(FactorGrantedAuthority.PASSWORD_AUTHORITY, NOW.minusSeconds(599))), null))).isTrue();
        }

        @Test
        @DisplayName("should deny when the only factor was issued outside the window")
        void shouldDenyWhenFactorIsStale() {
            assertThat(granted(manager().authorize(
                    auth(factor(FactorGrantedAuthority.PASSWORD_AUTHORITY, NOW.minusSeconds(601))), null))).isFalse();
        }

        @Test
        @DisplayName("should grant when any one of several factors is fresh")
        void shouldGrantWhenAnyFactorIsFresh() {
            // A stale password login plus a recent passkey assertion: the recent one is what matters.
            assertThat(granted(manager().authorize(
                    auth(factor(FactorGrantedAuthority.PASSWORD_AUTHORITY, NOW.minusSeconds(5000)),
                            factor(FactorGrantedAuthority.WEBAUTHN_AUTHORITY, NOW.minusSeconds(10))),
                    null))).isTrue();
        }

        @Test
        @DisplayName("should accept any factor kind, not only the configured step-up factors")
        void shouldAcceptAnyFactorKind() {
            // Requiring WEBAUTHN here would demand a passkey in order to register a first passkey.
            assertThat(granted(manager().authorize(
                    auth(factor(FactorGrantedAuthority.OTT_AUTHORITY, NOW.minusSeconds(10))), null))).isTrue();
        }
    }

    @Nested
    @DisplayName("Denial")
    class DenialTests {

        @Test
        @DisplayName("should deny when the session carries no factor at all")
        void shouldDenyWhenNoFactorPresent() {
            assertThat(granted(manager().authorize(auth(new SimpleGrantedAuthority("ROLE_USER")), null))).isFalse();
        }

        @Test
        @DisplayName("should deny a plain authority named like a factor")
        void shouldDenyLookAlikeAuthority() {
            // No issuedAt to read, so it cannot be fresh. FactorAuthorityNameValidator rejects such names at
            // startup; this is the runtime half of that defense.
            assertThat(granted(manager().authorize(
                    auth(new SimpleGrantedAuthority(FactorGrantedAuthority.PASSWORD_AUTHORITY)), null))).isFalse();
        }

        @Test
        @DisplayName("should deny an unauthenticated request")
        void shouldDenyUnauthenticated() {
            assertThat(granted(manager().authorize(() -> null, null))).isFalse();
        }

        @Test
        @DisplayName("should deny an anonymous request")
        void shouldDenyAnonymous() {
            Supplier<Authentication> anonymous = () -> new AnonymousAuthenticationToken("key", "anonymousUser",
                    List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
            assertThat(granted(manager().authorize(anonymous, null))).isFalse();
        }
    }
}
