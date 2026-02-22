package com.digitalsanctuary.spring.user.security;

import java.util.Collection;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Authentication token representing a successful WebAuthn/passkey authentication.
 *
 * <p>
 * This token replaces the use of {@link org.springframework.security.authentication.UsernamePasswordAuthenticationToken}
 * for WebAuthn logins, making it possible to distinguish passkey-based sessions from password-based sessions
 * in security logic and audit trails.
 * </p>
 *
 * <p>
 * The principal is the application's {@link UserDetails} (typically {@code DSUserDetails}), and
 * {@link #getCredentials()} returns {@code null} because no password is involved in WebAuthn authentication.
 * </p>
 */
public class WebAuthnAuthenticationToken extends AbstractAuthenticationToken {

	private static final long serialVersionUID = 1L;

	private final UserDetails principal;

	/**
	 * Creates a new WebAuthn authentication token.
	 *
	 * @param principal the authenticated user details
	 * @param authorities the granted authorities
	 */
	public WebAuthnAuthenticationToken(UserDetails principal, Collection<? extends GrantedAuthority> authorities) {
		super(authorities);
		this.principal = principal;
		setAuthenticated(true);
	}

	@Override
	public Object getCredentials() {
		return null;
	}

	@Override
	public Object getPrincipal() {
		return principal;
	}
}
