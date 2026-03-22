package com.digitalsanctuary.spring.user.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import com.digitalsanctuary.spring.user.persistence.model.User;
import lombok.Builder;
import lombok.ToString;

/**
 * The {@code DSUserDetails} class is an extension of the default Spring Security {@code UserDetails} interface that uses a custom {@code User} object
 * and email address as the username. This class provides implementations of the {@code UserDetails} and {@code OAuth2User} interfaces for use in
 * Spring Security authentication and authorization workflows.
 *
 * <p>
 * Instances of this class are created with a {@code User} object and an optional collection of {@code GrantedAuthority} objects that define the
 * user's authorization permissions. The class provides methods for accessing the user's information, such as the username (email address), password,
 * enabled status, and full name.
 *
 * <p>
 * This class also implements the {@code OAuth2User} interface, which allows it to be used in conjunction with OAuth2 authentication providers. The
 * class provides methods for retrieving the user's attributes and name from the OAuth2 provider, which can be useful for applications that need to
 * customize the user experience based on the provider or the user's attributes.
 *
 * <p>
 * Example usage:
 *
 * <pre>{@code
 * // Create a new DSUserDetails object for a user
 * User user = userRepository.findByEmail("user@example.com");
 * Collection<GrantedAuthority> authorities = Arrays.asList(new SimpleGrantedAuthority("ROLE_USER"));
 * DSUserDetails userDetails = new DSUserDetails(user, authorities);
 * }</pre>
 */
@ToString
public class DSUserDetails implements UserDetails, OidcUser {

	/** The Constant serialVersionUID. */
	private static final long serialVersionUID = 5286810064622508389L;

	/** The user. */
	private final User user;

	/** The granted authorities. */
	private final Collection<? extends GrantedAuthority> grantedAuthorities;

	/** The attributes. */
	private Map<String, Object> attributes;

	/** The Oidc user properties. */
	private OidcUserInfo oidcUserInfo;

	/** The Oidc user token. */
	private OidcIdToken oidcIdToken;

	/**
	 * Instantiates a new DS user details with OAuth2 provider attributes.
	 *
	 * @param user the user
	 * @param grantedAuthorities the granted authorities (optional, default = empty list)
	 * @param attributes the OAuth2 provider attributes (optional, falls back to User entity fields)
	 */
	public DSUserDetails(User user, Collection<? extends GrantedAuthority> grantedAuthorities, Map<String, Object> attributes) {
		this.user = user;
		this.grantedAuthorities = grantedAuthorities != null ? grantedAuthorities : new ArrayList<>();
		this.attributes = attributes != null ? new HashMap<>(attributes) : buildFallbackAttributes(user);
	}

	/**
	 * Instantiates a new DS user details without provider attributes. Attributes will be populated
	 * from the {@link User} entity fields as a fallback.
	 *
	 * @param user the user
	 * @param grantedAuthorities the granted authorities (optional, default = empty list)
	 */
	public DSUserDetails(User user, Collection<? extends GrantedAuthority> grantedAuthorities) {
		this(user, grantedAuthorities, (Map<String, Object>) null);
	}

	/**
	 * Instantiates a new DS user details with no granted authorities.
	 *
	 * @param user the user
	 */
	public DSUserDetails(User user) {
		this(user, null, (Map<String, Object>) null);
	}

	/**
	 * Instantiates a new DS user details with OIDC tokens and provider attributes.
	 *
	 * @param user the user
	 * @param oidcUserInfo containing claims about the user
	 * @param oidcIdToken containing claims about the user
	 * @param grantedAuthorities the granted authorities (optional, default = empty list)
	 * @param attributes the OAuth2/OIDC provider attributes (optional, falls back to idToken claims or User entity)
	 */
	@Builder
	public DSUserDetails(User user, OidcUserInfo oidcUserInfo, OidcIdToken oidcIdToken,
			Collection<? extends GrantedAuthority> grantedAuthorities, Map<String, Object> attributes) {
		this.user = user;
		this.oidcUserInfo = oidcUserInfo;
		this.oidcIdToken = oidcIdToken;
		this.grantedAuthorities = grantedAuthorities != null ? grantedAuthorities : new ArrayList<>();
		if (attributes != null) {
			this.attributes = new HashMap<>(attributes);
		} else if (oidcIdToken != null) {
			this.attributes = new HashMap<>(oidcIdToken.getClaims());
		} else {
			this.attributes = buildFallbackAttributes(user);
		}
	}

	/**
	 * Instantiates a new DS user details with OIDC tokens. Attributes will be populated from the
	 * OIDC ID token claims or {@link User} entity as a fallback.
	 *
	 * @param user the user
	 * @param oidcUserInfo containing claims about the user
	 * @param oidcIdToken containing claims about the user
	 * @param grantedAuthorities the granted authorities (optional, default = empty list)
	 */
	public DSUserDetails(User user, OidcUserInfo oidcUserInfo, OidcIdToken oidcIdToken,
			Collection<? extends GrantedAuthority> grantedAuthorities) {
		this(user, oidcUserInfo, oidcIdToken, grantedAuthorities, null);
	}

	/**
	 * Instantiates a new DS user details with OIDC tokens and no authorities.
	 *
	 * @param user the user
	 * @param oidcUserInfo containing claims about the user
	 * @param oidcIdToken containing claims about the user
	 */
	public DSUserDetails(User user, OidcUserInfo oidcUserInfo, OidcIdToken oidcIdToken) {
		this(user, oidcUserInfo, oidcIdToken, null, null);
	}

	/**
	 * Builds a fallback attributes map from the {@link User} entity fields. Used when no provider
	 * attributes are available (e.g., local/password login).
	 *
	 * @param user the user entity
	 * @return a map containing available user fields using standard OAuth2/OIDC claim names
	 */
	private static Map<String, Object> buildFallbackAttributes(User user) {
		Map<String, Object> attrs = new HashMap<>();
		if (user.getEmail() != null) {
			attrs.put("email", user.getEmail());
		}
		if (user.getFirstName() != null) {
			attrs.put("given_name", user.getFirstName());
		}
		if (user.getLastName() != null) {
			attrs.put("family_name", user.getLastName());
		}
		if (user.getFirstName() != null || user.getLastName() != null) {
			attrs.put("name", user.getFullName());
		}
		return attrs;
	}

	/**
	 * Gets the authorities.
	 *
	 * @return the authorities
	 */
	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return grantedAuthorities;
	}

	/**
	 * Gets the password.
	 *
	 * @return the password
	 */
	@Override
	public String getPassword() {
		return user.getPassword();
	}

	/**
	 * Gets the username.
	 *
	 * @return the username
	 */
	@Override
	public String getUsername() {
		return user.getEmail();
	}

	/**
	 * Checks if is account non expired.
	 *
	 * @return true, if is account non expired
	 */
	@Override
	public boolean isAccountNonExpired() {
		return true;
	}

	/**
	 * Checks if is account non locked.
	 *
	 * @return true, if is account non locked
	 */
	@Override
	public boolean isAccountNonLocked() {
		return !user.isLocked();
	}

	/**
	 * Checks if is credentials non expired.
	 *
	 * @return true, if is credentials non expired
	 */
	@Override
	public boolean isCredentialsNonExpired() {
		return true;
	}

	/**
	 * Checks if is enabled.
	 *
	 * @return true, if is enabled
	 */
	@Override
	public boolean isEnabled() {
		return user.isEnabled();
	}

	/**
	 * Gets the user.
	 *
	 * @return the user
	 */
	public User getUser() {
		return user;
	}

	@Override
	public Map<String, Object> getAttributes() {
		return attributes;
	}

	@Override
	public String getName() {
		return user.getFullName();
	}

	@Override
	public Map<String, Object> getClaims() {
		return oidcUserInfo != null ? oidcUserInfo.getClaims() : Map.of();
	}

	@Override
	public OidcUserInfo getUserInfo() {
		return oidcUserInfo;
	}

	@Override
	public OidcIdToken getIdToken() {
		return oidcIdToken;
	}
}
