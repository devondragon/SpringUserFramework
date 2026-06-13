package com.digitalsanctuary.spring.user.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.test.annotations.DatabaseTest;
import com.digitalsanctuary.spring.user.test.builders.UserTestDataBuilder;

/**
 * Repository slice tests for {@link UserRepository}, focusing on the atomic failed-login-attempt increment used to prevent the lockout-evasion
 * lost-update race.
 */
@DatabaseTest
class UserRepositoryTest {

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private TestEntityManager entityManager;

	@Test
	void incrementFailedAttemptsAtomicallyIncreasesCounterAndReturnsRowsAffected() {
		User user = UserTestDataBuilder.aUser().withId(null).withEmail("increment@test.com").withFailedLoginAttempts(0).build();
		entityManager.persistAndFlush(user);
		entityManager.clear();

		int firstUpdated = userRepository.incrementFailedAttempts("increment@test.com");
		int secondUpdated = userRepository.incrementFailedAttempts("increment@test.com");

		assertThat(firstUpdated).isEqualTo(1);
		assertThat(secondUpdated).isEqualTo(1);

		User reloaded = userRepository.findByEmail("increment@test.com");
		assertThat(reloaded).isNotNull();
		assertThat(reloaded.getFailedLoginAttempts()).isEqualTo(2);
	}

	@Test
	void incrementFailedAttemptsReturnsZeroForNonExistentEmail() {
		int updated = userRepository.incrementFailedAttempts("does-not-exist@test.com");

		assertThat(updated).isZero();
	}
}
