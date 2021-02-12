package com.digitalsanctuary.spring.user.service;

import java.util.ArrayList;
import java.util.Collection;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.digitalsanctuary.spring.user.persistence.model.User;

import lombok.ToString;

/**
 * The DSUserDetails class extends the default Spring Security UserDetails to use our User object and use the email
 * address as the username.
 */
@ToString
public class DSUserDetails implements UserDetails {

	/** The Constant serialVersionUID. */
	private static final long serialVersionUID = 5286810064622508389L;

	/** The user. */
	private final User user;

	/** The granted authorities. */
	private final Collection<? extends GrantedAuthority> grantedAuthorities;

	/**
	 * Instantiates a new DS user details.
	 *
	 * @param user
	 *            the user
	 */
	public DSUserDetails(User user) {
		this(user, new ArrayList<GrantedAuthority>());
	}

	/**
	 * Instantiates a new DS user details.
	 *
	 * @param user
	 *            the user
	 * @param grantedAuthorities
	 *            the granted authorities
	 */
	public DSUserDetails(User user, Collection<? extends GrantedAuthority> grantedAuthorities) {
		this.user = user;
		this.grantedAuthorities = grantedAuthorities;
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

}
