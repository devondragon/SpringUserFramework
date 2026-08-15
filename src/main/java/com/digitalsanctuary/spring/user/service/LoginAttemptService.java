package com.digitalsanctuary.spring.user.service;

import java.util.Date;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.repository.UserRepository;
import com.digitalsanctuary.spring.user.security.UserSecurityConfigProperties;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


/**
 * Service for tracking login attempts and implementing account lockout protection.
 *
 * <p>Tracks successful and failed login attempts per user account. When failed attempts exceed the
 * configured threshold ({@code user.security.failedLoginAttempts}), the account is locked for the
 * duration specified by {@code user.security.accountLockoutDuration}.</p>
 *
 * <p>For IP-based blocking and rate limiting, see Bucket4J and the Bucket4J Spring Boot Starter.
 * More information: <a href="https://github.com/devondragon/SpringUserFramework/issues/57">GitHub Issue #57</a></p>
 *
 * @see User#failedLoginAttempts
 * @see User#locked
 */
@Slf4j
@RequiredArgsConstructor
@Service("dsLoginAttemptService")
@Data
public class LoginAttemptService {

	final private UserRepository userRepository;

	/** The user security configuration properties. */
	final private UserSecurityConfigProperties userSecurityConfig;

	/**
	 * The configured maximum number of failed login attempts before an account is locked
	 * ({@code user.security.failedLoginAttempts}). Retained as a public accessor for backward
	 * compatibility with consumers that read it directly.
	 *
	 * @return the configured maximum failed login attempts
	 */
	public int getMaxFailedLoginAttempts() {
		return userSecurityConfig.getFailedLoginAttempts();
	}

	/**
	 * The configured account lockout duration in minutes ({@code user.security.accountLockoutDuration}).
	 * Retained as a public accessor for backward compatibility with consumers that read it directly.
	 *
	 * @return the configured account lockout duration in minutes
	 */
	public int getAccountLockoutDuration() {
		return userSecurityConfig.getAccountLockoutDuration();
	}

	/**
	 * Login succeeded, reset failed login attempts.
	 *
	 * @param email the email address of the user
	 */
	@Transactional
	public void loginSucceeded(final String email) {
		log.debug("Login succeeded for user: {}", email);
		User user = userRepository.findByEmail(email);
		if (user != null) {
			user.setFailedLoginAttempts(0);
			user.setLocked(false);
			user.setLockedDate(null);
			userRepository.save(user);
		}
	}

	/**
	 * Login failed.
	 *
	 * @param email the email address of the user
	 */
	@Transactional
	public void loginFailed(final String email) {
		log.debug("Login attempt failed for user: {}", email);
		if (userSecurityConfig.getFailedLoginAttempts() > 0) {
			// Atomically increment the counter via a single DB UPDATE to avoid the lost-update race that a read-modify-write would suffer under
			// concurrent failed logins (which could let an attacker evade lockout).
			int updated = userRepository.incrementFailedAttempts(email);
			if (updated == 0) {
				log.warn("User not found for email: {}", email);
				return;
			}
			// Re-read the fresh user; thanks to clearAutomatically on the bulk update, this reflects the true incremented count from the database.
			User user = userRepository.findByEmail(email);
			if (user != null && user.getFailedLoginAttempts() >= userSecurityConfig.getFailedLoginAttempts() && !user.isLocked()) {
				// Setting locked is idempotent if two threads both observe the threshold; the COUNTER is what must not lose updates.
				user.setLocked(true);
				user.setLockedDate(new Date());
				userRepository.save(user);
			}
		}
	}

	/**
	 * Checks if the user account is locked.
	 *
	 * @param email the email address (which is the username) of the user
	 * @return true, if the user account is currently locked
	 */
	public boolean isLocked(final String email) {
		log.debug("Checking if user is locked: {}", email);
		User user = userRepository.findByEmail(email);
		if (user != null && user.isLocked()) {
			// See if the user will be automatically unlocked
			user = checkIfUserShouldBeUnlocked(user);
			// If the user is still locked, return true
			if (user != null && user.isLocked()) {
				log.debug("User is locked: {}", email);
				return true;
			}
		}
		log.debug("User is not locked: {}", email);
		return false;
	}

	/**
	 * Check if user should be unlocked, and unlock the user if necessary.
	 *
	 * @param user the user
	 * @return the user
	 */
	public User checkIfUserShouldBeUnlocked(User user) {
		log.debug("Checking if user should be unlocked: {}", user.getEmail());
		if (user.isLocked() && user.getLockedDate() != null && userSecurityConfig.getAccountLockoutDuration() >= 0) {
			Date lockedDate = user.getLockedDate();
			Date now = new Date();
			long diff = now.getTime() - lockedDate.getTime();
			long diffMinutes = diff / (60 * 1000);
			if (diffMinutes >= userSecurityConfig.getAccountLockoutDuration()) {
				log.debug("User should be unlocked: {}", user.getEmail());
				user.setLocked(false);
				user.setLockedDate(null);
				user.setFailedLoginAttempts(0);
				userRepository.save(user);
			}
		}
		return user;
	}
}
