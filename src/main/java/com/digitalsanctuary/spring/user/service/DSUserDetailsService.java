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
import com.digitalsanctuary.spring.user.util.UserUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * The DSUserDetailsService extends the Spring Security UserDetailsService to use the DSUserDetails object and to use email as username.
 */
@Slf4j
@Service
@Transactional
public class DSUserDetailsService implements UserDetailsService {

	/** The user repository. */
	private UserRepository userRepository;

	/** The login attempt service. */
	private LoginAttemptService loginAttemptService;

	/** The request. */
	private HttpServletRequest request;

	/**
	 * Instantiates a new DSUserDetailsService object.
	 *
	 * @param userRepository the user repository
	 * @param loginAttemptService the login attempt service
	 * @param request the HTTP servlet request
	 */
	public DSUserDetailsService(UserRepository userRepository, LoginAttemptService loginAttemptService, HttpServletRequest request) {
		super();
		this.userRepository = userRepository;
		this.loginAttemptService = loginAttemptService;
		this.request = request;
	}

	/**
	 * Load user details by email address.
	 *
	 * @param email the email address
	 * @return the user details object
	 * @throws UsernameNotFoundException if no user is found with the provided email address
	 */
	@Override
	public DSUserDetails loadUserByUsername(final String email) throws UsernameNotFoundException {
		log.debug("DSUserDetailsService.loadUserByUsername:" + "called with username: {}", email);
		final String ip = UserUtils.getClientIP(request);
		if (loginAttemptService.isBlocked(ip)) {
			throw new RuntimeException("blocked");
		}

		try {
			final User user = userRepository.findByEmail(email);
			if (user == null) {
				throw new UsernameNotFoundException("No user found with email/username: " + email);
			}
			// Updating lastActivity date for this login
			user.setLastActivityDate(new Date());
			userRepository.save(user);
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
