package com.digitalsanctuary.spring.user.jobs;

import java.time.Instant;
import java.util.Date;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.digitalsanctuary.spring.user.persistence.repository.PasswordResetTokenRepository;
import com.digitalsanctuary.spring.user.persistence.repository.VerificationTokenRepository;
import lombok.extern.slf4j.Slf4j;

/**
 * The ExpiredTokenCleanJob is a Service which purges expired registration email verification tokens and password reset tokens based on the schedule
 * defined in user.purgetokens.cron.expression in your application.properties.
 */
@Slf4j
@Service
@Transactional
public class ExpiredTokenCleanJob {

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
		log.info("ExpiredTokenCleanJob.purgeExpired: running....");
		Date now = Date.from(Instant.now());

		passwordTokenRepository.deleteAllExpiredSince(now);
		verificaitonTokenRepository.deleteAllExpiredSince(now);
		log.info("ExpiredTokenCleanJob.purgeExpired: all expired tokens have been deleted.");
	}
}
