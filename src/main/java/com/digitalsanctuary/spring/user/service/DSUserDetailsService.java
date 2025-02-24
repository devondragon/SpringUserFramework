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
 * DSUserDetailsService is an implementation of Spring Security's UserDetailsService. It is responsible for loading user-specific data during
 * authentication.
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
