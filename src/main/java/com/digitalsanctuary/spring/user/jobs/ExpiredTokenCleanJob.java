package com.digitalsanctuary.spring.user.jobs;

import java.time.Instant;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.digitalsanctuary.spring.user.persistence.repository.PasswordResetTokenRepository;
import com.digitalsanctuary.spring.user.persistence.repository.VerificationTokenRepository;

/**
 * The ExpiredTokenCleanJob is a Service which purges expired registration email verification tokens and password reset
 * tokens based on the schedule defined in user.purgetokens.cron.expression in your application.properties.
 */
@Service
@Transactional
public class ExpiredTokenCleanJob {

	/** The logger. */
	public Logger logger = LoggerFactory.getLogger(this.getClass());

	/** The registration email verificaiton token repository. */
	@Autowired
	VerificationTokenRepository verificaitonTokenRepository;

	/** The password reset token repository. */
	@Autowired
	PasswordResetTokenRepository passwordTokenRepository;

	/**
	 * Purge expired.
	 */
	@Scheduled(cron = "${user.purgetokens.cron.expression}")
	public void purgeExpired() {
		logger.info("ExpiredTokenCleanJob.purgeExpired:" + "running....");
		Date now = Date.from(Instant.now());

		passwordTokenRepository.deleteAllExpiredSince(now);
		verificaitonTokenRepository.deleteAllExpiredSince(now);
		logger.info("ExpiredTokenCleanJob.purgeExpired:" + "all expired tokens have been deleted.");
	}
}
