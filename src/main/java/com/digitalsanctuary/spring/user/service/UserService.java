package com.digitalsanctuary.spring.user.service;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.digitalsanctuary.spring.user.dto.UserDto;
import com.digitalsanctuary.spring.user.exceptions.UserAlreadyExistException;
import com.digitalsanctuary.spring.user.persistence.model.PasswordResetToken;
import com.digitalsanctuary.spring.user.persistence.model.Privilege;
import com.digitalsanctuary.spring.user.persistence.model.Role;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.model.VerificationToken;
import com.digitalsanctuary.spring.user.persistence.repository.PasswordResetTokenRepository;
import com.digitalsanctuary.spring.user.persistence.repository.RoleRepository;
import com.digitalsanctuary.spring.user.persistence.repository.UserRepository;
import com.digitalsanctuary.spring.user.persistence.repository.VerificationTokenRepository;
import lombok.extern.slf4j.Slf4j;

/**
 * The UserService provides a lot of the logic for this framework.
 */
@Slf4j
@Service
@Transactional
public class UserService {

	private static final String USER_ROLE_NAME = "ROLE_USER";

	/** The Constant TOKEN_INVALID. */
	public static final String TOKEN_INVALID = "invalidToken";

	/** The Constant TOKEN_EXPIRED. */
	public static final String TOKEN_EXPIRED = "expired";

	/** The Constant TOKEN_VALID. */
	public static final String TOKEN_VALID = "valid";


	/** The user repository. */
	@Autowired
	UserRepository userRepository;

	/** The token repository. */
	@Autowired
	private VerificationTokenRepository tokenRepository;

	/** The password token repository. */
	@Autowired
	private PasswordResetTokenRepository passwordTokenRepository;

	/** The password encoder. */
	@Autowired
	private PasswordEncoder passwordEncoder;

	/** The role repository. */
	@Autowired
	private RoleRepository roleRepository;

	/** The session registry. */
	@Autowired
	private SessionRegistry sessionRegistry;

	/** The event publisher. */
	@Autowired
	ApplicationEventPublisher eventPublisher;

	@Autowired
	public UserEmailService userEmailService;

	@Autowired
	public UserVerificationService userVerificationService;

	/** The ds user details service. */
	@Autowired
	private DSUserDetailsService dsUserDetailsService;

	/** The send registration verification email. */
	@Value("${user.registration.sendVerificationEmail:false}")
	private boolean sendRegistrationVerificationEmail;

	/**
	 * Register new user account.
	 *
	 * @param newUserDto the new user dto
	 * @return the user
	 */
	public User registerNewUserAccount(final UserDto newUserDto) {
		log.debug("UserService.registerNewUserAccount: called with userDto: {}", newUserDto);
		if (emailExists(newUserDto.getEmail())) {
			log.debug("UserService.registerNewUserAccount:" + "email already exists: {}", newUserDto.getEmail());
			throw new UserAlreadyExistException("There is an account with that email address: " + newUserDto.getEmail());
		}

		// Create a new User entity
		final User user = new User();
		user.setFirstName(newUserDto.getFirstName());
		user.setLastName(newUserDto.getLastName());
		user.setPassword(passwordEncoder.encode(newUserDto.getPassword()));
		user.setEmail(newUserDto.getEmail());
		user.setRoles(Arrays.asList(roleRepository.findByName(USER_ROLE_NAME)));

		// If we are not sending a verification email
		if (!sendRegistrationVerificationEmail) {
			// Enable the user immediately
			user.setEnabled(true);
		}
		return userRepository.save(user);
	}

	/**
	 * Save registered user.
	 *
	 * @param user the user
	 */
	public void saveRegisteredUser(final User user) {
		userRepository.save(user);
	}

	/**
	 * Delete user.
	 *
	 * @param user the user
	 */
	public void deleteUser(final User user) {
		final VerificationToken verificationToken = tokenRepository.findByUser(user);

		if (verificationToken != null) {
			tokenRepository.delete(verificationToken);
		}

		final PasswordResetToken passwordToken = passwordTokenRepository.findByUser(user);

		if (passwordToken != null) {
			passwordTokenRepository.delete(passwordToken);
		}
		userRepository.delete(user);
	}

	/**
	 * Creates the password reset token for user.
	 *
	 * @param user the user
	 * @param token the token
	 */


	/**
	 * Find user by email.
	 *
	 * @param email the email
	 * @return the user
	 */
	public User findUserByEmail(final String email) {
		return userRepository.findByEmail(email);
	}

	/**
	 * Gets the password reset token.
	 *
	 * @param token the token
	 * @return the password reset token
	 */
	public PasswordResetToken getPasswordResetToken(final String token) {
		return passwordTokenRepository.findByToken(token);
	}

	/**
	 * Gets the user by password reset token.
	 *
	 * @param token the token
	 * @return the user by password reset token
	 */
	public Optional<User> getUserByPasswordResetToken(final String token) {
		return Optional.ofNullable(passwordTokenRepository.findByToken(token).getUser());
	}

	/**
	 * Gets the user by ID.
	 *
	 * @param id the id
	 * @return the user by ID
	 */
	public Optional<User> getUserByID(final long id) {
		return userRepository.findById(id);
	}

	/**
	 * Change user password.
	 *
	 * @param user the user
	 * @param password the password
	 */
	public void changeUserPassword(final User user, final String password) {
		user.setPassword(passwordEncoder.encode(password));
		userRepository.save(user);
	}

	/**
	 * Check if valid old password.
	 *
	 * @param user the user
	 * @param oldPassword the old password
	 * @return true, if successful
	 */
	public boolean checkIfValidOldPassword(final User user, final String oldPassword) {
		return passwordEncoder.matches(oldPassword, user.getPassword());
	}

	/**
	 * Email exists.
	 *
	 * @param email the email
	 * @return true, if successful
	 */
	private boolean emailExists(final String email) {
		return userRepository.findByEmail(email) != null;
	}

	/**
	 * Validate password reset token.
	 *
	 * @param token the token
	 * @return the string
	 */
	public String validatePasswordResetToken(String token) {
		final PasswordResetToken passToken = passwordTokenRepository.findByToken(token);
		if (passToken == null) {
			return TOKEN_INVALID;
		}
		final Calendar cal = Calendar.getInstance();
		if (passToken.getExpiryDate().before(cal.getTime())) {
			passwordTokenRepository.delete(passToken);
			return TOKEN_EXPIRED;
		}
		return TOKEN_VALID;
	}

	/**
	 * Gets the users from session registry.
	 *
	 * @return the users from session registry
	 */
	public List<String> getUsersFromSessionRegistry() {
		return sessionRegistry.getAllPrincipals().stream().filter((u) -> !sessionRegistry.getAllSessions(u, false).isEmpty()).map(o -> {
			if (o instanceof User) {
				return ((User) o).getEmail();
			} else {
				return o.toString();
			}
		}).collect(Collectors.toList());
	}

	/**
	 * Auth without password.
	 *
	 * @param user the user
	 */
	public void authWithoutPassword(User user) {
		List<Privilege> privileges =
				user.getRoles().stream().map(Role::getPrivileges).flatMap(Collection::stream).distinct().collect(Collectors.toList());

		List<GrantedAuthority> authorities = privileges.stream().map(p -> new SimpleGrantedAuthority(p.getName())).collect(Collectors.toList());

		DSUserDetails userDetails = dsUserDetailsService.loadUserByUsername(user.getEmail());

		Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, null, authorities);
		SecurityContextHolder.getContext().setAuthentication(authentication);
	}

}
