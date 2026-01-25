package com.digitalsanctuary.spring.user.jobs;

import java.time.Instant;
import java.util.Date;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.digitalsanctuary.spring.user.persistence.repository.PasswordResetTokenRepository;
import com.digitalsanctuary.spring.user.persistence.repository.VerificationTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Scheduled job service that purges expired registration email verification tokens
 * and password reset tokens. Runs on a cron schedule defined by the
 * {@code user.purgetokens.cron.expression} property in application configuration.
 *
 * @see com.digitalsanctuary.spring.user.persistence.repository.VerificationTokenRepository
 * @see com.digitalsanctuary.spring.user.persistence.repository.PasswordResetTokenRepository
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ExpiredTokenCleanJob {

	/** The registration email verification token repository. */
	private final VerificationTokenRepository verificationTokenRepository;

	/** The password reset token repository. */
	private final PasswordResetTokenRepository passwordTokenRepository;

	/**
	 * Purge expired.
	 */
	@Scheduled(cron = "${user.purgetokens.cron.expression}")
	public void purgeExpired() {
		log.info("ExpiredTokenCleanJob.purgeExpired: running....");
		Date now = Date.from(Instant.now());

		passwordTokenRepository.deleteAllExpiredSince(now);
		verificationTokenRepository.deleteAllExpiredSince(now);
		log.info("ExpiredTokenCleanJob.purgeExpired: all expired tokens have been deleted.");
	}
}
