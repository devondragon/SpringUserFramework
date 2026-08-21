package com.digitalsanctuary.spring.user.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;

/**
 * Names the authentication factor each password-less login path actually represents.
 *
 * <p>
 * {@link UserService#authWithoutPassword} builds its own {@code Authentication} rather than running a Spring
 * Security {@code AuthenticationProvider}, so nothing stamps a {@link FactorGrantedAuthority} on the session. A
 * session with no factor cannot satisfy any freshness check, which would leave a just-registered user unable to
 * enroll their first passkey until they logged out and back in.
 * </p>
 *
 * <p>
 * The factor is chosen per call site rather than defaulted, because the paths prove different things: clicking an
 * emailed verification link is a one-time token, auto-login straight after registration follows a password the user
 * submitted in that same request, and dev login proves nothing at all.
 * </p>
 */
public enum AuthWithoutPasswordFactor {

    /** Clicking the emailed verification link. Possession of a one-time token. */
    EMAIL_VERIFICATION(FactorGrantedAuthority.OTT_AUTHORITY),

    /** Auto-login immediately after registration, when email verification is disabled. */
    REGISTRATION(FactorGrantedAuthority.PASSWORD_AUTHORITY),

    /** Dev-only impersonation ({@code local} profile). Proves nothing about presence, so stamps nothing. */
    DEV_LOGIN(null);

    private final String authority;

    AuthWithoutPasswordFactor(String authority) {
        this.authority = authority;
    }

    /**
     * Returns the factor authority this path stamps.
     *
     * @return the authority string, or {@code null} when the path stamps no factor
     */
    public String getAuthority() {
        return authority;
    }

    /**
     * Returns the given authorities plus a freshly issued factor for this path.
     *
     * @param authorities the authorities resolved for the user
     * @return a new list with the factor appended, or the authorities unchanged when this path stamps none
     */
    public List<GrantedAuthority> withFactor(Collection<? extends GrantedAuthority> authorities) {
        if (authority == null) {
            return List.copyOf(authorities);
        }
        List<GrantedAuthority> result = new ArrayList<>(authorities);
        result.add(FactorGrantedAuthority.fromAuthority(authority));
        return List.copyOf(result);
    }
}
