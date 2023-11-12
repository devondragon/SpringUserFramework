package com.digitalsanctuary.spring.user.service;

import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.repository.UserRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


/**
 * The LoginAttemptService can be used to track successful and failed logins by Username, and can be used to block attacks on user accounts. For IP
 * based blocking and rate limiting see Bucket4J and the Bucket4J Spring Boot Starter. More info can found here -
 * https://github.com/devondragon/SpringUserFramework/issues/57
 */
@Slf4j
@RequiredArgsConstructor
@Service
@Data
public class LoginAttemptService {

	final private UserRepository userRepository;

	/** The max failed login attempts on a given account before it is locked. A value of 0 will disable locking accounts based on failed logins. */
	@Value("${user.security.failedLoginAttempts}")
	private int failedLoginAttempts;

	/**
	 * The account lockout duration. A value less than 0 means accounts can only be unlocked by action, not duration. A value of 0 means account
	 * lockouts are disabled. A value greater than 0 is the number of minutes that an account will stay locked before automatically unlocking.
	 */
	@Value("${user.security.accountLockoutDuration}")
	private int accountLockoutDuration;

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
		if (failedLoginAttempts > 0) {
			User user = userRepository.findByEmail(email);
			if (user != null) {
				incrementFailedAttempts(user);
			} else {
				log.warn("User not found for email: {}", email);
			}
		}
	}

	/**
	 * Increment failed attempts.
	 *
	 * @param user the user
	 */
	private void incrementFailedAttempts(User user) {
		int currentAttempts = user.getFailedLoginAttempts();
		user.setFailedLoginAttempts(++currentAttempts);
		if (currentAttempts >= failedLoginAttempts) {
			user.setLocked(true);
			user.setLockedDate(new Date());
		}
		userRepository.save(user);
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
	 * @param user
	 * @return
	 */
	public User checkIfUserShouldBeUnlocked(User user) {
		log.debug("Checking if user should be unlocked: {}", user.getEmail());
		if (user.isLocked() && user.getLockedDate() != null && accountLockoutDuration >= 0) {
			Date lockedDate = user.getLockedDate();
			Date now = new Date();
			long diff = now.getTime() - lockedDate.getTime();
			long diffMinutes = diff / (60 * 1000);
			if (diffMinutes >= accountLockoutDuration) {
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
