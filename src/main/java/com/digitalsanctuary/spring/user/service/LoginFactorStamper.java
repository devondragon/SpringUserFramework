package com.digitalsanctuary.spring.user.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;

/**
 * Ensures a successful login leaves a freshness signal on the session.
 *
 * <p>
 * Spring Security stamps a {@link FactorGrantedAuthority} on some login flows but not all. Verified against 7.1.0:
 * the password provider stamps {@code FACTOR_PASSWORD} and {@code OAuth2LoginAuthenticationProvider} stamps
 * {@code FACTOR_AUTHORIZATION_CODE}, but {@code OidcAuthorizationCodeAuthenticationProvider} stamps nothing. An OIDC
 * login therefore produces a session with no factor at all, which can never satisfy a freshness requirement.
 * </p>
 *
 * <p>
 * The test is whether a factor is already present, not what kind of authentication this is. On both the OAuth2 and
 * the OIDC path this framework's user services return a {@code DSUserDetails} carrying database-derived authorities,
 * so the usual {@code OidcUserAuthority} versus {@code OAuth2UserAuthority} discriminator does not survive here.
 * Checking for the factor itself is both simpler and correct for any future flow that leaves one out.
 * </p>
 */
public final class LoginFactorStamper {

    private LoginFactorStamper() {
    }

    /**
     * Returns the given authorities, adding a freshly issued {@code FACTOR_AUTHORIZATION_CODE} only when no factor
     * is present at all.
     *
     * @param authorities the authorities the login produced
     * @return the authorities, with a factor guaranteed present
     */
    public static List<GrantedAuthority> ensureFactor(Collection<? extends GrantedAuthority> authorities) {
        boolean alreadyStamped = authorities.stream().anyMatch(FactorGrantedAuthority.class::isInstance);
        if (alreadyStamped) {
            return List.copyOf(authorities);
        }
        List<GrantedAuthority> result = new ArrayList<>(authorities);
        result.add(FactorGrantedAuthority.fromAuthority(FactorGrantedAuthority.AUTHORIZATION_CODE_AUTHORITY));
        return List.copyOf(result);
    }
}
