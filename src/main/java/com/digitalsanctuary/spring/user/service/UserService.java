package com.digitalsanctuary.spring.user.service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.digitalsanctuary.spring.user.dto.PasswordlessRegistrationDto;
import com.digitalsanctuary.spring.user.dto.UserDto;
import com.digitalsanctuary.spring.user.event.UserDeletedEvent;
import com.digitalsanctuary.spring.user.event.UserDisabledEvent;
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
import com.digitalsanctuary.spring.user.registration.RegistrationContext;
import com.digitalsanctuary.spring.user.registration.RegistrationDecision;
import com.digitalsanctuary.spring.user.registration.RegistrationDeniedException;
import com.digitalsanctuary.spring.user.registration.RegistrationGuard;
import com.digitalsanctuary.spring.user.registration.RegistrationSource;
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

	private final SessionInvalidationService sessionInvalidationService;

	/** Hashes tokens before they are stored / looked up at rest. */
	private final TokenHasher tokenHasher;

	/**
	 * The registration guard, enforced on every registration path (form, passwordless) in this service
	 * so that direct callers cannot bypass it. Resolves to the primary
	 * {@link com.digitalsanctuary.spring.user.registration.CompositeRegistrationGuard composite guard},
	 * which applies first-deny-wins across all configured guards.
	 */
	private final RegistrationGuard registrationGuard;

	/**
	 * Self-reference, resolved through the Spring proxy, used to invoke the transactional persistence
	 * methods from the non-transactional public entry points.
	 *
	 * <p>
	 * bcrypt hashing is deliberately slow (~100ms+). Running it inside an open transaction holds a
	 * pooled DB connection for the full hash and starves the pool under load. The public entry methods
	 * are therefore annotated {@link Propagation#NOT_SUPPORTED} so they run with no transaction (no
	 * connection held) while the encode happens, then delegate the actual DB write to a short
	 * {@code @Transactional} persist method invoked <em>through this proxy reference</em>. Calling the
	 * persist method directly ({@code this.persistX(...)}) would be a self-invocation that bypasses the
	 * proxy, so the transaction would never start — hence the proxied self-reference. It is injected
	 * {@link Lazy} to break the construction-time circular dependency on itself.
	 * </p>
	 */
	@Lazy
	@Autowired
	private UserService self;

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
	 * <p>
	 * Runs with {@link Isolation#SERIALIZABLE} isolation to close the duplicate-registration
	 * race when two requests register the same email concurrently. The {@link #emailExists}
	 * pre-check handles the common case, but a concurrent insert can still fail at commit; in
	 * that case the resulting {@link DataIntegrityViolationException} (unique-constraint
	 * violation) or serialization failure ({@link CannotAcquireLockException} /
	 * {@link ConcurrencyFailureException}) is translated into a {@link UserAlreadyExistException}
	 * (HTTP 409) rather than surfacing as a 500. Unrelated failures are never swallowed.
	 * </p>
	 *
	 * @implNote This method is {@link Propagation#NOT_SUPPORTED}: the slow bcrypt hash runs with no
	 *           transaction (and no pooled connection) held, and the DB write is delegated to a
	 *           short, separate transaction. As a result this method does <em>not</em> enlist in a
	 *           caller's transaction — if a consumer calls it from inside their own
	 *           {@code @Transactional}, that outer transaction is suspended and the registration
	 *           commits independently, so an outer rollback will not undo the persisted user.
	 *
	 * @return the newly created user entity
	 * @throws UserAlreadyExistException if an account with the same email already
	 *                                   exists
	 */
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public User registerNewUserAccount(final UserDto newUserDto) {
		TimeLogger timeLogger = new TimeLogger(log, "UserService.registerNewUserAccount");
		log.debug("UserService.registerNewUserAccount: called with userDto: {}", newUserDto);

		if (newUserDto.getPassword() == null) {
			throw new IllegalArgumentException(
					"Password is required for standard registration. Use registerPasswordlessAccount() for passwordless accounts.");
		}

		// Validate password match only if both are provided
		if (newUserDto.getMatchingPassword() != null
				&& !newUserDto.getPassword().equals(newUserDto.getMatchingPassword())) {
			throw new IllegalArgumentException("Passwords do not match");
		}

		// Enforce the RegistrationGuard for every form registration — including direct callers of this
		// service method — before doing any (slow) work. A denial throws RegistrationDeniedException,
		// which the UserAPI translates into the REGISTRATION_DENIED response.
		evaluateRegistrationGuard(newUserDto.getEmail(), RegistrationSource.FORM, null);

		// Create a new User entity. The (deliberately slow) bcrypt encode runs HERE, with NO
		// transaction active (this method is Propagation.NOT_SUPPORTED), so it never holds a pooled
		// DB connection. The DB write happens afterward in the short, proxied persistNewUserAccount.
		User user = new User();
		user.setFirstName(newUserDto.getFirstName());
		user.setLastName(newUserDto.getLastName());
		user.setPassword(passwordEncoder.encode(newUserDto.getPassword()));
		user.setEmail(newUserDto.getEmail().toLowerCase());

		// If we are not sending a verification email
		if (!sendRegistrationVerificationEmail) {
			// Enable the user immediately
			user.setEnabled(true);
		}

		// Persist through the proxy so the SERIALIZABLE transaction actually applies (a direct
		// this.persistNewUserAccount(...) self-invocation would bypass the proxy and run no transaction).
		User saved = self.persistNewUserAccount(user);
		// authWithoutPassword(saved);
		timeLogger.end();
		return saved;
	}

	/**
	 * Persists a new user account inside a short, serializable transaction.
	 *
	 * <p>
	 * This is the DB-only half of {@link #registerNewUserAccount(UserDto)}: the password has already
	 * been encoded by the (non-transactional) caller, so no slow bcrypt work happens while this
	 * connection-holding transaction is open. It runs with {@link Isolation#SERIALIZABLE} to close the
	 * duplicate-registration race when two requests register the same email concurrently. The
	 * {@link #emailExists} pre-check handles the common case, but a concurrent insert can still fail at
	 * commit; in that case the resulting {@link DataIntegrityViolationException} (unique-constraint
	 * violation) or serialization failure ({@link CannotAcquireLockException} /
	 * {@link ConcurrencyFailureException}) is translated into a {@link UserAlreadyExistException}
	 * (HTTP 409) rather than surfacing as a 500. Unrelated failures are never swallowed.
	 * </p>
	 *
	 * <p>
	 * Internal seam: this method exists only to split the DB write away from the bcrypt hash. It MUST
	 * be invoked through the Spring proxy (via {@link #self}) so the transaction applies. It is
	 * deliberately <b>package-private</b> so consumers cannot call it directly and bypass the
	 * centralized RegistrationGuard enforced by {@link #registerNewUserAccount(UserDto)}; CGLIB
	 * self-invocation still applies the transaction because Spring's proxy subclass is generated in
	 * this same package.
	 * </p>
	 *
	 * @param user the fully built user entity (password already encoded)
	 * @return the saved user entity
	 * @throws UserAlreadyExistException if an account with the same email already exists
	 */
	@Transactional(isolation = Isolation.SERIALIZABLE)
	User persistNewUserAccount(final User user) {
		if (emailExists(user.getEmail())) {
			log.debug("UserService.persistNewUserAccount: email already exists: {}", user.getEmail());
			throw new UserAlreadyExistException(
					"There is an account with that email address: " + user.getEmail());
		}

		user.setRoles(Arrays.asList(roleRepository.findByName(USER_ROLE_NAME)));

		try {
			User saved = userRepository.save(user);
			savePasswordHistory(saved, saved.getPassword());
			return saved;
		} catch (DataIntegrityViolationException | ConcurrencyFailureException e) {
			// A concurrent registration won the race: the unique-email constraint was violated
			// (DataIntegrityViolationException) or the SERIALIZABLE transaction could not be
			// serialized (ConcurrencyFailureException, e.g. CannotAcquireLockException). Translate
			// to a 409 instead of letting it surface as a 500. Only these duplicate/serialization
			// cases are translated; unrelated exceptions propagate unchanged.
			log.debug("UserService.persistNewUserAccount: concurrent registration detected for email {}: {}",
					user.getEmail(), e.getClass().getSimpleName());
			throw new UserAlreadyExistException(
					"There is an account with that email address: " + user.getEmail());
		}
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
	 *
	 * <p>
	 * This method runs within the caller's class-level transaction (it is invoked via
	 * self-invocation, so any method-level {@code @Transactional} would be bypassed by the proxy
	 * and never apply). Rather than load every history row and {@code deleteAll} the overflow
	 * (a read-then-delete window that races with concurrent inserts), it issues a single
	 * set-based, bounded delete:
	 * </p>
	 * <ol>
	 * <li>Locate the id of the oldest entry to keep (the {@code maxEntries}-th most recent entry,
	 * ordered by primary key descending).</li>
	 * <li>Delete all of the user's entries with an id strictly less than that cutoff.</li>
	 * </ol>
	 *
	 * <p>
	 * Ordering by id is reliable because the id is generated with {@code GenerationType.IDENTITY}
	 * and is therefore monotonically increasing. The approach is portable across H2, MariaDB, and
	 * PostgreSQL (no subquery {@code LIMIT}) and is tolerant of being called repeatedly.
	 * </p>
	 *
	 * @param user the user whose password history should be cleaned up
	 */
	private void cleanUpPasswordHistory(User user) {
		if (user == null || historyCount <= 0) {
			return;
		}

		// Keep historyCount + 1 entries: the current password plus historyCount previous passwords.
		// This ensures we actually prevent reuse of the last historyCount passwords.
		int maxEntries = historyCount + 1;

		// Fetch only the cutoff row: the oldest entry we want to keep (0-based index maxEntries - 1,
		// newest first). Everything older than this is pruned.
		List<Long> cutoffIds =
				passwordHistoryRepository.findIdsByUserOrderByIdDesc(user, PageRequest.of(maxEntries - 1, 1));
		if (cutoffIds.isEmpty()) {
			// Fewer than maxEntries rows exist; nothing to prune.
			return;
		}

		Long cutoffId = cutoffIds.get(0);
		int deleted = passwordHistoryRepository.deleteByUserAndIdLessThan(user, cutoffId);
		if (deleted > 0) {
			log.debug("Cleaned up {} old password history entries for user: {}", deleted, user.getEmail());
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
		log.debug("UserService.deleteOrDisableUser: called for user: {}", user != null ? user.getEmail() : null);
		if (actuallyDeleteAccount) {
			log.debug("UserService.deleteOrDisableUser: actuallyDeleteAccount is true, deleting user: {}", user.getEmail());
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

			// Publish UserDeletedEvent AFTER the surrounding transaction commits. The event is
			// primarily consumed by external applications (often via @Async listeners) that must
			// not observe a not-yet-committed deletion. There is no framework-internal listener
			// for this event, so rather than annotate a listener we defer publication itself via a
			// transaction synchronization. If no transaction is active (e.g. called outside a
			// transactional context), fall back to publishing immediately.
			publishEventAfterCommit(new UserDeletedEvent(this, userId, userEmail));
		} else {
			log.debug("UserService.deleteOrDisableUser: actuallyDeleteAccount is false, disabling user: {}", user.getEmail());
			// Capture user details before the save for the post-commit event, mirroring the delete path.
			Long userId = user.getId();
			String userEmail = user.getEmail();

			user.setEnabled(false);
			userRepository.save(user);
			log.debug("UserService.deleteOrDisableUser: user {} has been disabled", user.getEmail());

			// Publish UserDisabledEvent AFTER the surrounding transaction commits so listeners (often
			// @Async, in consuming apps) never observe a not-yet-committed change. This makes the default
			// soft-delete path observable, matching the hard-delete path's UserDeletedEvent.
			publishEventAfterCommit(new UserDisabledEvent(this, userId, userEmail));
		}
	}

	/**
	 * Publishes the given application event after the current transaction commits.
	 *
	 * <p>
	 * If a transaction is active, the event is published from
	 * {@link TransactionSynchronization#afterCommit()} so listeners (especially {@code @Async}
	 * ones) never act on a change that has not yet been committed. If no transaction is active,
	 * the event is published immediately so the behavior is still correct in non-transactional
	 * callers. Used for both {@link UserDeletedEvent} (hard delete) and {@link UserDisabledEvent}
	 * (soft delete).
	 * </p>
	 *
	 * @param event the event to publish after commit
	 */
	private void publishEventAfterCommit(final ApplicationEvent event) {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					log.debug("Publishing {} after commit", event.getClass().getSimpleName());
					eventPublisher.publishEvent(event);
				}
			});
		} else {
			log.debug("Publishing {} (no active transaction)", event.getClass().getSimpleName());
			eventPublisher.publishEvent(event);
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
	 * Resolves a password reset token by its raw value using a dual-read strategy.
	 *
	 * <p>
	 * Tokens are stored hashed, so we first look up by {@code hash(rawToken)}. For backward
	 * compatibility we fall back to looking up by the raw value, which resolves any pre-upgrade
	 * tokens that were stored in plaintext before token hashing was introduced. This fallback is
	 * permanently safe and needs no operator action to retire: every token carries an
	 * {@code expiryDate} bounded by the configured lifetime, and the validate path rejects expired
	 * tokens, so any lingering plaintext token becomes unusable within its lifetime window.
	 * </p>
	 *
	 * @param rawToken the raw token value
	 * @return the resolved token entity, or {@code null} if not found
	 */
	private PasswordResetToken resolvePasswordResetToken(final String rawToken) {
		if (rawToken == null) {
			return null;
		}
		PasswordResetToken token = passwordTokenRepository.findByToken(tokenHasher.hash(rawToken));
		if (token == null) {
			token = passwordTokenRepository.findByToken(rawToken);
		}
		return token;
	}

	/**
	 * Gets the password reset token.
	 *
	 * @param token the raw token
	 * @return the password reset token
	 */
	public PasswordResetToken getPasswordResetToken(final String token) {
		return resolvePasswordResetToken(token);
	}

	/**
	 * Gets the user by password reset token.
	 *
	 * @param token the raw token
	 * @return the user by password reset token
	 */
	public Optional<User> getUserByPasswordResetToken(final String token) {
		PasswordResetToken passwordResetToken = resolvePasswordResetToken(token);
		if (passwordResetToken == null) {
			return Optional.empty();
		}
		return Optional.ofNullable(passwordResetToken.getUser());
	}

	/**
	 * Deletes a password reset token after it has been used.
	 *
	 * <p>
	 * Uses dual-delete to match the dual-read lookup: deletes the hashed value first, then falls back
	 * to the raw value to clean up any pre-upgrade plaintext token.
	 * </p>
	 *
	 * @param token the raw token string to delete
	 */
	public void deletePasswordResetToken(final String token) {
		if (token == null) {
			return;
		}
		int deletedCount = passwordTokenRepository.deleteByToken(tokenHasher.hash(token));
		if (deletedCount == 0) {
			deletedCount = passwordTokenRepository.deleteByToken(token);
		}
		if (deletedCount > 0) {
			log.debug("Deleted used password reset token.");
		}
	}

	/**
	 * Atomically validates and consumes a password reset token in a single transaction.
	 *
	 * <p>
	 * This prevents a token from being double-consumed: validation and deletion happen together, so
	 * two concurrent reset attempts cannot both succeed with the same token. Returns the associated
	 * user when the token is valid (and deletes it), or {@code null} when the token is missing or
	 * expired (expired tokens are also deleted as a cleanup). Uses dual-read so both hashed
	 * (post-upgrade) and plaintext (pre-upgrade) tokens resolve.
	 * </p>
	 *
	 * @param token the raw token to validate and consume
	 * @return the user associated with the token if it was valid, otherwise {@code null}
	 */
	@Transactional
	public User validateAndConsumePasswordResetToken(final String token) {
		final PasswordResetToken passToken = resolvePasswordResetToken(token);
		if (passToken == null) {
			return null;
		}
		final Calendar cal = Calendar.getInstance();
		if (passToken.getExpiryDate().before(cal.getTime())) {
			passwordTokenRepository.delete(passToken);
			return null;
		}
		final User user = passToken.getUser();
		// Consume the token immediately so it cannot be reused.
		passwordTokenRepository.delete(passToken);
		return user;
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
	 *
	 * @implNote This method is {@link Propagation#NOT_SUPPORTED}: the slow bcrypt hash runs with no
	 *           transaction (and no pooled connection) held, and the DB write is delegated to a
	 *           short, separate transaction. As a result this method does <em>not</em> enlist in a
	 *           caller's transaction — if a consumer calls it from inside their own
	 *           {@code @Transactional}, that outer transaction is suspended and the password change
	 *           commits independently, so an outer rollback will not undo it.
	 */
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public void changeUserPassword(final User user, final String password) {
		// Encode the new password with NO transaction active (this method is
		// Propagation.NOT_SUPPORTED) so the slow bcrypt hash never holds a pooled DB connection.
		String encodedPassword = passwordEncoder.encode(password);
		user.setPassword(encodedPassword);
		// Persist through the proxy so the short transaction applies.
		self.persistChangedPassword(user, encodedPassword);
	}

	/**
	 * Persists a changed password inside a short transaction.
	 *
	 * <p>
	 * The DB-only half of {@link #changeUserPassword(User, String)}: the password has already been
	 * encoded by the (non-transactional) caller, so no bcrypt work happens while this transaction holds
	 * a connection. Saves the user, records password history, and invalidates all existing sessions so
	 * a reset/change forces re-auth everywhere (OWASP).
	 * </p>
	 *
	 * <p>
	 * Internal seam: this method exists only to split the DB write away from the bcrypt hash. It MUST
	 * be invoked through the Spring proxy (via {@link #self}) so the transaction applies. It is
	 * deliberately <b>package-private</b> so it is not part of the public API; CGLIB self-invocation
	 * still applies the transaction because Spring's proxy subclass is generated in this same package.
	 * </p>
	 *
	 * @param user            the user whose password changed (password field already set/encoded)
	 * @param encodedPassword the already-encoded password to record in history
	 */
	@Transactional
	void persistChangedPassword(final User user, final String encodedPassword) {
		userRepository.save(user);
		savePasswordHistory(user, encodedPassword);
		// Force re-auth on a password change (OWASP). By default the current session is preserved and
		// regenerated and only the user's OTHER sessions are invalidated, so the user is not logged out
		// of the device they just used; set user.session.invalidation.keep-current-session-on-password-change=false
		// to terminate every session including the current one.
		sessionInvalidationService.invalidateSessionsAfterPasswordChange(user);
	}

	/**
	 * Check if valid old password.
	 *
	 * @param user        the user
	 * @param oldPassword the old password
	 * @return true, if successful
	 */
	public boolean checkIfValidOldPassword(final User user, final String oldPassword) {
		log.debug("Verifying old password for user: {}", user.getEmail());
		if (user.getPassword() == null) {
			return false;
		}
		return passwordEncoder.matches(oldPassword, user.getPassword());
	}

	/**
	 * Checks whether the user has a password set.
	 *
	 * @param user the user to check
	 * @return true if the user has a non-empty password
	 */
	public boolean hasPassword(User user) {
		return user.getPassword() != null && !user.getPassword().isEmpty();
	}

	/**
	 * Removes the user's password, making the account passwordless.
	 * Also clears all password history entries for the user.
	 *
	 * @param user the user whose password should be removed
	 */
	@Transactional
	public void removeUserPassword(User user) {
		user.setPassword(null);
		userRepository.save(user);
		passwordHistoryRepository.deleteByUser(user);
		// Same policy as a password change: by default preserve+regenerate the current session and invalidate
		// only the user's other sessions (see user.session.invalidation.keep-current-session-on-password-change).
		sessionInvalidationService.invalidateSessionsAfterPasswordChange(user);
		log.info("Password removed for user: {}", user.getEmail());
	}

	/**
	 * Sets an initial password for a passwordless account.
	 * Throws if the user already has a password.
	 *
	 * @param user the user to set the password for
	 * @param rawPassword the raw password to encode and save
	 * @throws IllegalStateException if the user already has a password
	 *
	 * @implNote This method is {@link Propagation#NOT_SUPPORTED}: the slow bcrypt hash runs with no
	 *           transaction (and no pooled connection) held, and the DB write is delegated to a
	 *           short, separate transaction. As a result this method does <em>not</em> enlist in a
	 *           caller's transaction — if a consumer calls it from inside their own
	 *           {@code @Transactional}, that outer transaction is suspended and the password change
	 *           commits independently, so an outer rollback will not undo it.
	 */
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public void setInitialPassword(User user, String rawPassword) {
		if (hasPassword(user)) {
			throw new IllegalStateException("User already has a password");
		}
		// Encode with NO transaction active (this method is Propagation.NOT_SUPPORTED) so the slow
		// bcrypt hash never holds a pooled DB connection. The DB write runs in the proxied persist.
		String encodedPassword = passwordEncoder.encode(rawPassword);
		user.setPassword(encodedPassword);
		// Persist through the proxy so the short transaction applies.
		self.persistInitialPassword(user, encodedPassword);
		log.info("Initial password set for user: {}", user.getEmail());
	}

	/**
	 * Persists an initial password inside a short transaction.
	 *
	 * <p>
	 * The DB-only half of {@link #setInitialPassword(User, String)}: the password has already been
	 * encoded by the (non-transactional) caller, so no bcrypt work happens while this transaction holds
	 * a connection. Saves the user and records password history.
	 * </p>
	 *
	 * <p>
	 * Internal seam: this method exists only to split the DB write away from the bcrypt hash. It MUST
	 * be invoked through the Spring proxy (via {@link #self}) so the transaction applies. It is
	 * deliberately <b>package-private</b> so it is not part of the public API; CGLIB self-invocation
	 * still applies the transaction because Spring's proxy subclass is generated in this same package.
	 * </p>
	 *
	 * @param user            the user whose initial password is being set (password field already set)
	 * @param encodedPassword the already-encoded password to record in history
	 */
	@Transactional
	void persistInitialPassword(final User user, final String encodedPassword) {
		userRepository.save(user);
		savePasswordHistory(user, encodedPassword);
	}

	/**
	 * Registers a new passwordless user account (no password).
	 * Uses SERIALIZABLE isolation to prevent race conditions during concurrent registration.
	 *
	 * @param dto the passwordless registration data
	 * @return the newly created user entity
	 * @throws UserAlreadyExistException if an account with the same email already exists
	 */
	@Transactional(isolation = Isolation.SERIALIZABLE)
	public User registerPasswordlessAccount(final PasswordlessRegistrationDto dto) {
		TimeLogger timeLogger = new TimeLogger(log, "UserService.registerPasswordlessAccount");
		log.debug("UserService.registerPasswordlessAccount: called for email: {}", dto != null ? dto.getEmail() : null);

		// Enforce the RegistrationGuard for every passwordless registration — including direct callers of
		// this service method. A denial throws RegistrationDeniedException, which the UserAPI translates
		// into the REGISTRATION_DENIED response.
		evaluateRegistrationGuard(dto.getEmail(), RegistrationSource.PASSWORDLESS, null);

		if (emailExists(dto.getEmail())) {
			log.debug("UserService.registerPasswordlessAccount: email already exists: {}", dto.getEmail());
			throw new UserAlreadyExistException(
					"There is an account with that email address: " + dto.getEmail());
		}

		User user = new User();
		user.setFirstName(dto.getFirstName());
		user.setLastName(dto.getLastName());
		user.setPassword(null);
		user.setEmail(dto.getEmail().toLowerCase());
		user.setRoles(Arrays.asList(roleRepository.findByName(USER_ROLE_NAME)));

		if (!sendRegistrationVerificationEmail) {
			user.setEnabled(true);
		}

		user = userRepository.save(user);
		timeLogger.end();
		return user;
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
	 * Evaluates the configured {@link RegistrationGuard} for a registration attempt and throws a
	 * {@link RegistrationDeniedException} if it is denied.
	 *
	 * <p>This centralizes guard enforcement in the service so that <em>every</em> registration path —
	 * including direct callers of the public registration methods — is guarded exactly once with the
	 * correct {@link RegistrationSource}. The injected guard is the primary
	 * {@link com.digitalsanctuary.spring.user.registration.CompositeRegistrationGuard composite}, so all
	 * configured guards are applied with first-deny-wins semantics.</p>
	 *
	 * @param email        the email address of the registration attempt (may be {@code null})
	 * @param source       the registration source; never {@code null}
	 * @param providerName the OAuth2/OIDC provider registration id, or {@code null} for form/passwordless
	 * @throws RegistrationDeniedException if the guard denies the registration
	 */
	private void evaluateRegistrationGuard(final String email, final RegistrationSource source, final String providerName) {
		RegistrationDecision decision = registrationGuard.evaluate(new RegistrationContext(email, source, providerName));
		if (!decision.allowed()) {
			log.info("Registration denied for source: {} provider: {} reason: {}", source, providerName, decision.reason());
			throw new RegistrationDeniedException(decision.reason());
		}
	}

	/**
	 * Enforces the configured {@link RegistrationGuard} for a first-time OAuth2/OIDC social registration.
	 *
	 * <p>The OAuth2 and OIDC user services build and persist new social users themselves (with
	 * provider-specific role assignment and audit events). To keep guard enforcement centralized in this
	 * service — so the guard SPI lives in exactly one place and direct callers of the registration paths
	 * cannot bypass it — those services delegate the guard check here at the point a NEW social user is
	 * about to be created (never on login of an existing OAuth/OIDC user). On denial this throws
	 * {@link RegistrationDeniedException}, which the OAuth/OIDC services translate into the appropriate
	 * {@code OAuth2AuthenticationException}.</p>
	 *
	 * @param email        the email address from the OAuth2/OIDC provider
	 * @param source       the registration source ({@link RegistrationSource#OAUTH2} or
	 *                     {@link RegistrationSource#OIDC})
	 * @param providerName the OAuth2/OIDC provider registration id (e.g. {@code "google"}, {@code "keycloak"})
	 * @throws RegistrationDeniedException if the guard denies the registration
	 */
	public void enforceRegistrationGuard(final String email, final RegistrationSource source, final String providerName) {
		evaluateRegistrationGuard(email, source, providerName);
	}

	/**
	 * Validate password reset token.
	 *
	 * @param token the token
	 * @return the password reset token validation result enum
	 */
	public TokenValidationResult validatePasswordResetToken(String token) {
		final PasswordResetToken passToken = resolvePasswordResetToken(token);
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
		log.debug("UserService.authWithoutPassword: authenticating user: {}", user != null ? user.getEmail() : null);
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

		// Publish authentication event for listeners (session profile, brute-force reset)
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null) {
			eventPublisher.publishEvent(new InteractiveAuthenticationSuccessEvent(authentication, this.getClass()));
		}

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
		// Ensure a session exists before attempting to rotate its id.
		request.getSession(true);

		// Defend against session fixation on this programmatic-login path: issue a new session id
		// (existing attributes are preserved) so a pre-auth fixed id cannot be reused post-authentication (OWASP).
		try {
			request.changeSessionId();
		} catch (IllegalStateException e) {
			// No active session to rotate (shouldn't happen after getSession(true)); fall back to a fresh session below.
			log.warn("UserService.storeSecurityContextInSession: could not rotate session id: {}", e.getMessage());
		}

		// Store the security context on the (now rotated) session.
		HttpSession session = request.getSession(true);
		session.setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
	}

}
