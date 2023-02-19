package com.digitalsanctuary.spring.user.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

/**
 * The DSUserDetailsService extends the Spring Security UserDetailsService to use the DSUserDetails object and to use email as username.
 */
@Service
@Transactional
public class DSUserDetailsService implements UserDetailsService {

	/** The logger. */
	public Logger logger = LoggerFactory.getLogger(this.getClass());

	/** The user repository. */
	@Autowired
	private UserRepository userRepository;

	/** The login attempt service. */
	@Autowired
	private LoginAttemptService loginAttemptService;

	/** The request. */
	@Autowired
	private HttpServletRequest request;

	/**
	 * Instantiates a new DS user details service.
	 */
	public DSUserDetailsService() {
		super();
	}

	/**
	 * Load user by email address.
	 *
	 * @param email the email address
	 * @return the DS user details
	 * @throws UsernameNotFoundException the email not found exception
	 */
	@Override
	public DSUserDetails loadUserByUsername(final String email) throws UsernameNotFoundException {
		logger.debug("DSUserDetailsService.loadUserByUsername:" + "called with username: {}", email);
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
			DSUserDetails userDetails = new DSUserDetails(user, getAuthorities(user.getRoles()));
			return userDetails;
		} catch (final Exception e) {
			logger.error("DSUserDetailsService.loadUserByUsername:" + "Exception!", e);
			throw new RuntimeException(e);
		}
	}

	/**
	 * Gets the authorities.
	 *
	 * @param roles the roles
	 * @return the authorities
	 */
	private Collection<? extends GrantedAuthority> getAuthorities(final Collection<Role> roles) {
		return getGrantedAuthorities(getPrivileges(roles));
	}

	/**
	 * Gets the privileges.
	 *
	 * @param roles the roles
	 * @return the privileges
	 */
	private List<String> getPrivileges(final Collection<Role> roles) {
		final List<String> privileges = new ArrayList<>();
		final List<Privilege> collection = new ArrayList<>();
		for (final Role role : roles) {
			collection.addAll(role.getPrivileges());
		}
		for (final Privilege item : collection) {
			privileges.add(item.getName());
		}

		return privileges;
	}

	/**
	 * Gets the granted authorities.
	 *
	 * @param privileges the privileges
	 * @return the granted authorities
	 */
	private List<GrantedAuthority> getGrantedAuthorities(final List<String> privileges) {
		final List<GrantedAuthority> authorities = new ArrayList<>();
		for (final String privilege : privileges) {
			authorities.add(new SimpleGrantedAuthority(privilege));
		}
		return authorities;
	}

}
