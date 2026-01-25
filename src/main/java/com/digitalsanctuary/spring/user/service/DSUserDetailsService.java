package com.digitalsanctuary.spring.user.service;

import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Spring Security {@link UserDetailsService} implementation for loading user authentication data.
 *
 * <p>This service retrieves user information from the database by email address and constructs
 * the {@link DSUserDetails} object used by Spring Security during authentication.</p>
 *
 * @see UserDetailsService
 * @see DSUserDetails
 */
@Slf4j
@RequiredArgsConstructor
@Service
@Transactional
public class DSUserDetailsService implements UserDetailsService {

	/** The user repository. */
	private final UserRepository userRepository;

	private final LoginHelperService loginHelperService;

	/** The request. */
	// private final HttpServletRequest request;

	/**
	 * Load user details by email address.
	 *
	 * @param email the email address
	 * @return the user details object
	 * @throws UsernameNotFoundException if no user is found with the provided email address
	 */
	@Override
	public DSUserDetails loadUserByUsername(final String email) throws UsernameNotFoundException {
		log.debug("DSUserDetailsService.loadUserByUsername: called with username: {}", email);
		User dbUser = userRepository.findByEmail(email);
		if (dbUser == null) {
			throw new UsernameNotFoundException("No user found with email/username: " + email);
		}
		return loginHelperService.userLoginHelper(dbUser);
	}

}
