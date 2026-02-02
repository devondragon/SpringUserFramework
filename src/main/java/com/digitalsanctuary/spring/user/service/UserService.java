package com.digitalsanctuary.spring.user.service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.digitalsanctuary.spring.user.dto.UserDto;
import com.digitalsanctuary.spring.user.event.UserDeletedEvent;
import com.digitalsanctuary.spring.user.event.UserPreDeleteEvent;
import com.digitalsanctuary.spring.user.exceptions.UserAlreadyExistException;
import com.digitalsanctuary.spring.user.persistence.model.PasswordHistoryEntry;
import com.digitalsanctuary.spring.user.persistence.model.PasswordResetToken;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.model.VerificationToken;
import com.digitalsanctuary.spring.user.persistence.repository.PasswordHistoryRepository;
import com.digitalsanctuary.spring.user.persistence.repository.PasswordResetTokenRepository;
import com.digitalsanctuary.spring.user.persistence.repository.RoleRepository;
import com.digitalsanctuary.spring.user.persistence.repository.UserRepository;
import com.digitalsanctuary.spring.user.persistence.repository.VerificationTokenRepository;
import com.digitalsanctuary.spring.user.util.TimeLogger;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service class for managing users. Provides methods for user registration,
 * authentication, password management, and user-related operations. This
 * class is transactional and uses various repositories and services for its
 * operations.
 *
 * <p>
 * This class is transactional, meaning that any failure causes the entire
 * operation to roll back to the previous state.
 * </p>
 *
 * <p>
 * Dependencies:
 * </p>
 * <ul>
 * <li>{@link UserRepository}</li>
 * <li>{@link VerificationTokenRepository}</li>
 * <li>{@link PasswordResetTokenRepository}</li>
 * <li>{@link PasswordEncoder}</li>
 * <li>{@link RoleRepository}</li>
 * <li>{@link SessionRegistry}</li>
 * <li>{@link UserEmailService}</li>
 * <li>{@link UserVerificationService}</li>
 * <li>{@link DSUserDetailsService}</li>
 * </ul>
 *
 * <p>
 * Configuration:
 * </p>
 * <ul>
 * <li>sendRegistrationVerificationEmail: Flag to determine if a verification
 * email should be sent upon registration.</li>
 * </ul>
 *
 * <p>
 * Enum:
 * </p>
 * <ul>
 * <li>{@link TokenValidationResult}: Enum representing the result of token
 * validation.</li>
 * </ul>
 *
 * <p>
 * Methods:
 * </p>
 * <ul>
 * <li>{@link #registerNewUserAccount(UserDto)}: Registers a new user
 * account.</li>
 * <li>{@link #saveRegisteredUser(User)}: Saves a registered user.</li>
 * <li>{@link #deleteOrDisableUser(User)}: Deletes a user and cleans up
 * associated tokens.</li>
 * <li>{@link #findUserByEmail(String)}: Finds a user by email.</li>
 * <li>{@link #getPasswordResetToken(String)}: Gets a password reset token by
 * token string.</li>
 * <li>{@link #getUserByPasswordResetToken(String)}: Gets a user by password
 * reset token.</li>
 * <li>{@link #findUserByID(long)}: Finds a user by ID.</li>
 * <li>{@link #changeUserPassword(User, String)}: Changes the user's
 * password.</li>
 * <li>{@link #checkIfValidOldPassword(User, String)}: Checks if the provided
 * old password is valid.</li>
 * <li>{@link #validatePasswordResetToken(String)}: Validates a password reset
 * token.</li>
 * <li>{@link #getUsersFromSessionRegistry()}: Gets the list of users from the
 * session registry.</li>
 * <li>{@link #authWithoutPassword(User)}: Authenticates a user without a
 * password.</li>
 * </ul>
 *
 * <p>
 * Private Methods:
 * </p>
 * <ul>
 * <li>{@link #emailExists(String)}: Checks if an email exists in the user
 * repository.</li>
 * <li>{@link #authenticateUser(DSUserDetails, Collection)}: Authenticates a
 * user by setting the authentication object in the security context.</li>
 * <li>{@link #storeSecurityContextInSession()}: Stores the current security
 * context in the session.</li>
 * </ul>
 *
 * <p>
 * Annotations:
 * </p>
 * <ul>
 * <li>{@link Slf4j}: For logging.</li>
 * <li>{@link Service}: Indicates that this class is a service component in
 * Spring.</li>
 * <li>{@link RequiredArgsConstructor}: Generates a constructor with required
 * arguments.</li>
 * <li>{@link Transactional}: Indicates that the class or methods should be
 * transactional.</li>
 * <li>{@link Value}: Injects property values.</li>
 * </ul>
 *
 * @author Devon Hillard
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

	/**
	 * Enum representing the result of token validation.
	 */
	public enum TokenValidationResult {

		/**
		 * Indicates that the token is valid and can be used.
		 */
		VALID("valid"),

		/**
		 * Indicates that the token is invalid, either due to tampering or an unknown
		 * format.
		 */
		INVALID_TOKEN("invalidToken"),

		/**
		 * Indicates that the token was valid but has expired and is no longer usable.
		 */
		EXPIRED("expired");

		private final String value;

		/**
		 * Instantiates a new token validation result.
		 *
		 * @param value the string representation of the token validation result.
		 */
		TokenValidationResult(String value) {
			this.value = value;
		}

		/**
		 * Gets the string representation of the token validation result.
		 *
		 * @return the value of the token validation result.
		 */
		public String getValue() {
			return value;
		}
	}

	/** The user role name. */
	private static final String USER_ROLE_NAME = "ROLE_USER";

	/** The user repository. */
	private final UserRepository userRepository;

	/** The token repository. */
	private final VerificationTokenRepository tokenRepository;

	/** The password token repository. */
	private final PasswordResetTokenRepository passwordTokenRepository;

	/** The password encoder. */
	private final PasswordEncoder passwordEncoder;

	/** The role repository. */
	private final RoleRepository roleRepository;

	/** The session registry. */
	private final SessionRegistry sessionRegistry;

	/** The user email service. */
	public final UserEmailService userEmailService;

	/** The user verification service. */
	public final UserVerificationService userVerificationService;

	private final AuthorityService authorityService;

	/** The user details service. */
	private final DSUserDetailsService dsUserDetailsService;

	private final ApplicationEventPublisher eventPublisher;

	private final PasswordHistoryRepository passwordHistoryRepository;

	/** The send registration verification email flag. */
	@Value("${user.registration.sendVerificationEmail:false}")
	private boolean sendRegistrationVerificationEmail;

	@Value("${user.actuallyDeleteAccount:false}")
	private boolean actuallyDeleteAccount;

	@Value("${user.security.password.history-count:0}")
	private int historyCount;

	/**
	 * Registers a new user account with the provided user data. If the email
	 * already exists, throws a UserAlreadyExistException. If
	 * sendRegistrationVerificationEmail is false, the user is enabled immediately.
	 *
	 * @param newUserDto the data transfer object containing the user registration
	 *                   information
	 * @return the newly created user entity
	 * @throws UserAlreadyExistException if an account with the same email already
	 *                                   exists
	 */
	public User registerNewUserAccount(final UserDto newUserDto) {
		TimeLogger timeLogger = new TimeLogger(log, "UserService.registerNewUserAccount");
		log.debug("UserService.registerNewUserAccount: called with userDto: {}", newUserDto);

		// Validate password match only if both are provided
		if (newUserDto.getPassword() != null && newUserDto.getMatchingPassword() != null
				&& !newUserDto.getPassword().equals(newUserDto.getMatchingPassword())) {
			throw new IllegalArgumentException("Passwords do not match");
		}

		if (emailExists(newUserDto.getEmail())) {
			log.debug("UserService.registerNewUserAccount: email already exists: {}", newUserDto.getEmail());
			throw new UserAlreadyExistException(
					"There is an account with that email address: " + newUserDto.getEmail());
		}

		// Create a new User entity
		User user = new User();
		user.setFirstName(newUserDto.getFirstName());
		user.setLastName(newUserDto.getLastName());
		user.setPassword(passwordEncoder.encode(newUserDto.getPassword()));
		user.setEmail(newUserDto.getEmail().toLowerCase());
		user.setRoles(Arrays.asList(roleRepository.findByName(USER_ROLE_NAME)));

		// If we are not sending a verification email
		if (!sendRegistrationVerificationEmail) {
			// Enable the user immediately
			user.setEnabled(true);
		}

		user = userRepository.save(user);
		savePasswordHistory(user, user.getPassword());
		// authWithoutPassword(user);
		timeLogger.end();
		return user;
	}

	/**
	 * Save registered user.
	 *
	 * @param user the user
	 * @return the user
	 */
	public User saveRegisteredUser(final User user) {
		return userRepository.save(user);
	}

	private void savePasswordHistory(User user, String encodedPassword) {
		if (user == null || !StringUtils.hasText(encodedPassword)) {
			log.warn("Cannot save password history: user or password is null/empty.");
			return;
		}

		PasswordHistoryEntry entry = new PasswordHistoryEntry(user, encodedPassword, LocalDateTime.now());
		passwordHistoryRepository.save(entry);
		log.debug("Password history entry saved for user: {}", user.getEmail());

		// Clean up old entries
		cleanUpPasswordHistory(user);
	}

	/**
	 * Cleans up old password history entries for a user, keeping only the most recent entries.
	 * Uses SERIALIZABLE isolation to prevent race conditions when the same user changes
	 * their password concurrently from multiple sessions.
	 *
	 * @param user the user whose password history should be cleaned up
	 */
	@Transactional(isolation = Isolation.SERIALIZABLE)
	private void cleanUpPasswordHistory(User user) {
		if (user == null || historyCount <= 0) {
			return;
		}

		List<PasswordHistoryEntry> entries = passwordHistoryRepository.findByUserOrderByEntryDateDesc(user);
		// Keep historyCount + 1 entries: the current password plus historyCount previous passwords
		// This ensures we actually prevent reuse of the last historyCount passwords
		int maxEntries = historyCount + 1;
		if (entries.size() > maxEntries) {
			List<PasswordHistoryEntry> toDelete = entries.subList(maxEntries, entries.size());
			passwordHistoryRepository.deleteAll(toDelete);
			log.debug("Cleaned up {} old password history entries for user: {}", toDelete.size(), user.getEmail());
		}
	}

	/**
	 * Delete user and clean up associated tokens. If actuallyDeleteAccount is true,
	 * the user is deleted from the database. Otherwise, the user is
	 * disabled.
	 *
	 * Transactional method to ensure that the operation is atomic. If any part of
	 * the operation fails, the entire transaction is rolled back. This
	 * includes the Event to allow the consuming application to handle data cleanup
	 * as needed before the User is deleted.
	 *
	 * @param user the user to delete or disable
	 */
	@Transactional
	public void deleteOrDisableUser(final User user) {
		log.debug("UserService.deleteOrDisableUser: called with user: {}", user);
		if (actuallyDeleteAccount) {
			log.debug("UserService.deleteOrDisableUser: actuallyDeleteAccount is true, deleting user: {}", user);
			// Capture user details before deletion for the post-delete event
			Long userId = user.getId();
			String userEmail = user.getEmail();

			// Publish the UserPreDeleteEvent before deleting the user
			// This allows any listeners to perform actions before the user is deleted
			log.debug("Publishing UserPreDeleteEvent");
			eventPublisher.publishEvent(new UserPreDeleteEvent(this, user));

			// Clean up any Tokens associated with this user
			final VerificationToken verificationToken = tokenRepository.findByUser(user);
			if (verificationToken != null) {
				tokenRepository.delete(verificationToken);
			}

			final PasswordResetToken passwordToken = passwordTokenRepository.findByUser(user);
			if (passwordToken != null) {
				passwordTokenRepository.delete(passwordToken);
			}
			// Delete the user
			userRepository.delete(user);

			// Publish UserDeletedEvent after successful deletion
			log.debug("Publishing UserDeletedEvent");
			eventPublisher.publishEvent(new UserDeletedEvent(this, userId, userEmail));
		} else {
			log.debug("UserService.deleteOrDisableUser: actuallyDeleteAccount is false, disabling user: {}", user);
			user.setEnabled(false);
			userRepository.save(user);
			log.debug("UserService.deleteOrDisableUser: user {} has been disabled", user.getEmail());
		}
	}

	/**
	 * Find user by email.
	 *
	 * @param email the email
	 * @return the user
	 */
	public User findUserByEmail(final String email) {
		if (email == null) {
			return null;
		}
		return userRepository.findByEmail(email.toLowerCase());
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
		if (token == null) {
			return Optional.empty();
		}
		PasswordResetToken passwordResetToken = passwordTokenRepository.findByToken(token);
		if (passwordResetToken == null) {
			return Optional.empty();
		}
		return Optional.ofNullable(passwordResetToken.getUser());
	}

	/**
	 * Deletes a password reset token after it has been used.
	 * Uses a direct DELETE query for efficiency (no SELECT required).
	 *
	 * @param token the token string to delete
	 */
	public void deletePasswordResetToken(final String token) {
		if (token == null) {
			return;
		}
		int deletedCount = passwordTokenRepository.deleteByToken(token);
		if (deletedCount > 0) {
			log.debug("Deleted password reset token: {}", token);
		}
	}

	/**
	 * Gets the user by ID.
	 *
	 * @param id the id
	 * @return the user by ID
	 */
	public Optional<User> findUserByID(final long id) {
		return userRepository.findById(id);
	}

	/**
	 * Change user password.
	 *
	 * @param user     the user
	 * @param password the password
	 */
	public void changeUserPassword(final User user, final String password) {
		String encodedPassword = passwordEncoder.encode(password);
		user.setPassword(encodedPassword);
		userRepository.save(user);
		savePasswordHistory(user, encodedPassword);
	}

	/**
	 * Check if valid old password.
	 *
	 * @param user        the user
	 * @param oldPassword the old password
	 * @return true, if successful
	 */
	public boolean checkIfValidOldPassword(final User user, final String oldPassword) {
		// Removed System.out.println, using log.debug for minimal output (avoid logging
		// passwords in production)
		log.debug("Verifying old password for user: {}", user.getEmail());
		return passwordEncoder.matches(oldPassword, user.getPassword());
	}

	/**
	 * See if the Email exists in the user repository.
	 *
	 * @param email the email address to lookup
	 * @return true, if the email address is already in the user repository
	 */
	private boolean emailExists(final String email) {
		return userRepository.findByEmail(email.toLowerCase()) != null;
	}

	/**
	 * Validate password reset token.
	 *
	 * @param token the token
	 * @return the password reset token validation result enum
	 */
	public TokenValidationResult validatePasswordResetToken(String token) {
		final PasswordResetToken passToken = passwordTokenRepository.findByToken(token);
		if (passToken == null) {
			return TokenValidationResult.INVALID_TOKEN;
		}
		final Calendar cal = Calendar.getInstance();
		if (passToken.getExpiryDate().before(cal.getTime())) {
			passwordTokenRepository.delete(passToken);
			return TokenValidationResult.EXPIRED;
		}
		return TokenValidationResult.VALID;
	}

	/**
	 * Gets the users from session registry.
	 *
	 * @return the users from session registry
	 */
	public List<String> getUsersFromSessionRegistry() {
		return sessionRegistry.getAllPrincipals().stream()
				.filter((u) -> !sessionRegistry.getAllSessions(u, false).isEmpty()).map(o -> {
					if (o instanceof User) {
						return ((User) o).getEmail();
					} else {
						return o.toString();
					}
				}).collect(Collectors.toList());
	}

	/**
	 * Authenticates the given user without requiring a password. This method loads
	 * the user's details, generates their authorities from their roles
	 * and privileges, and stores these details in the security context and session.
	 *
	 * <p>
	 * <strong>SECURITY WARNING:</strong> This is a potentially dangerous method as
	 * it authenticates a user without password verification. This method
	 * should only be used in specific controlled scenarios, such as after
	 * successful email verification or OAuth authentication.
	 * </p>
	 *
	 * @param user The user to authenticate without password verification
	 */
	public void authWithoutPassword(User user) {
		log.debug("UserService.authWithoutPassword: authenticating user: {}", user);
		if (user == null || user.getEmail() == null) {
			log.error("Invalid user or user email");
			return;
		}

		DSUserDetails userDetails;
		try {
			userDetails = dsUserDetailsService.loadUserByUsername(user.getEmail());
		} catch (UsernameNotFoundException e) {
			log.error("User not found: {}", user.getEmail(), e);
			return;
		}

		// Generate authorities from user roles and privileges
		Collection<? extends GrantedAuthority> authorities = authorityService.getAuthoritiesFromUser(user);

		// Authenticate user
		authenticateUser(userDetails, authorities);

		// Store security context in session
		storeSecurityContextInSession();

		log.debug("UserService.authWithoutPassword: authenticated user: {}", user.getEmail());
	}

	/**
	 * Authenticates the user by creating an authentication object and setting it in
	 * the security context.
	 *
	 * @param userDetails The user details.
	 * @param authorities The list of authorities for the user.
	 */
	private void authenticateUser(DSUserDetails userDetails, Collection<? extends GrantedAuthority> authorities) {
		Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, null, authorities);
		SecurityContextHolder.getContext().setAuthentication(authentication);
	}

	/**
	 * Stores the current security context in the session.
	 */
	private void storeSecurityContextInSession() {
		ServletRequestAttributes servletRequestAttributes = (ServletRequestAttributes) RequestContextHolder
				.getRequestAttributes();
		// Check if request attributes are available
		if (servletRequestAttributes == null) {
			log.error("Could not get request attributes");
			return;
		}

		HttpServletRequest request = servletRequestAttributes.getRequest();
		HttpSession session = request.getSession(true);

		// Store the security context in the session
		session.setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
	}

}
