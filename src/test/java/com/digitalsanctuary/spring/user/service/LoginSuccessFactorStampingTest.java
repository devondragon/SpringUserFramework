package com.digitalsanctuary.spring.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Not every login flow stamps a {@link FactorGrantedAuthority}. Verified in Spring Security 7.1.0: the password and
 * plain-OAuth2 providers do, but {@code OidcAuthorizationCodeAuthenticationProvider} does not, so an OIDC login
 * (Keycloak, or Google configured with the {@code openid} scope) leaves a session carrying no factor at all. Such a
 * session can never satisfy a freshness check, so those users could never enroll a passkey once the gate is on.
 * <p>
 * The discriminator is deliberately "is a factor already present" rather than the authentication's type: on both the
 * OAuth2 and OIDC paths this framework's user services return a {@code DSUserDetails} whose authorities are database
 * roles, so the usual {@code OidcUserAuthority} versus {@code OAuth2UserAuthority} test does not apply here.
 * </p>
 */
@DisplayName("Login Success Factor Stamping Tests")
class LoginSuccessFactorStampingTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("should stamp an authorization-code factor when the login flow stamped none")
    void shouldStampWhenNoFactorPresent() {
        Instant before = Instant.now().minusSeconds(1);
        Authentication oidcLogin = new TestingAuthenticationToken("user@test.com", "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_USER")));

        List<GrantedAuthority> result = LoginFactorStamper.ensureFactor(oidcLogin.getAuthorities());

        assertThat(result).extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_USER", FactorGrantedAuthority.AUTHORIZATION_CODE_AUTHORITY);
        FactorGrantedAuthority stamped = result.stream().filter(FactorGrantedAuthority.class::isInstance)
                .map(FactorGrantedAuthority.class::cast).findFirst().orElseThrow();
        assertThat(stamped.getIssuedAt()).isAfter(before);
    }

    @Test
    @DisplayName("should not stamp a second factor when the login flow already stamped one")
    void shouldNotStampWhenFactorAlreadyPresent() {
        // Form login and plain OAuth2 both stamp before the success handler runs. Adding again would duplicate,
        // and a stale duplicate could shadow the genuine one in a freshness check.
        Authentication passwordLogin = new TestingAuthenticationToken("user@test.com", "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_USER"),
                        FactorGrantedAuthority.fromAuthority(FactorGrantedAuthority.PASSWORD_AUTHORITY)));

        List<GrantedAuthority> result = LoginFactorStamper.ensureFactor(passwordLogin.getAuthorities());

        assertThat(result).containsExactlyElementsOf(passwordLogin.getAuthorities());
        assertThat(result).filteredOn(FactorGrantedAuthority.class::isInstance).hasSize(1);
    }
}
