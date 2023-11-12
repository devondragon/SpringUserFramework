package com.digitalsanctuary.spring.user.service;

import java.util.Collection;
import java.util.Date;
import java.util.stream.Collectors;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.digitalsanctuary.spring.user.persistence.model.Privilege;
import com.digitalsanctuary.spring.user.persistence.model.Role;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@RequiredArgsConstructor
@Service
@Transactional
/**
 * DSUserDetailsService is an implementation of Spring Security's UserDetailsService. It is responsible for loading user-specific data during
 * authentication.
 */
public class DSUserDetailsService implements UserDetailsService {

	/** The user repository. */
	private final UserRepository userRepository;

	/** The login attempt service. */
	private final LoginAttemptService loginAttemptService;

	/** The request. */
	// private final HttpServletRequest request;

	/**
	 * Load user details by email address.
	 *
	 * @param email the email address
	 * @return the user details object
	 * @throws UsernameNotFoundException if no user is found with the provided email address
	 * @throws CustomBlockedException if the request is coming from a blocked IP address
	 */
	@Override
	public DSUserDetails loadUserByUsername(final String email) throws UsernameNotFoundException {
		log.debug("DSUserDetailsService.loadUserByUsername:" + "called with username: {}", email);

		try {
			User user = userRepository.findByEmail(email);
			if (user == null) {
				throw new UsernameNotFoundException("No user found with email/username: " + email);
			}
			// Updating lastActivity date for this login
			user.setLastActivityDate(new Date());

			// Check if the user account is locked, but should be unlocked now, and unlock it
			user = loginAttemptService.checkIfUserShouldBeUnlocked(user);

			Collection<? extends GrantedAuthority> authorities = getAuthorities(user.getRoles());
			DSUserDetails userDetails = new DSUserDetails(user, authorities);
			return userDetails;
		} catch (final Exception e) {
			log.error("DSUserDetailsService.loadUserByUsername:" + "Exception!", e);
			throw new RuntimeException(e);
		}
	}

	/**
	 *
	 * Returns a collection of Spring Security's GrantedAuthority objects that corresponds to the privileges associated with the given collection of
	 * roles.
	 *
	 * @param roles a collection of roles whose privileges should be converted into Spring Security's GrantedAuthority objects
	 * @return a collection of Spring Security's GrantedAuthority objects that corresponds to the privileges associated with the given collection of
	 *         roles
	 */
	private Collection<? extends GrantedAuthority> getAuthorities(Collection<Role> roles) {
		// flatMap streams the roles, and maps each Role to its privileges (a Collection of Privilege objects).
		// The stream of Collection<Privilege> objects is then flattened into a single stream of Privilege objects.
		// Finally, each Privilege is mapped to its name as a String, wrapped in a SimpleGrantedAuthority object,
		// and collected into a Set of GrantedAuthority objects.
		return roles.stream().flatMap(role -> role.getPrivileges().stream()).map(Privilege::getName).map(SimpleGrantedAuthority::new)
				.collect(Collectors.toSet());
	}

}
