package com.digitalsanctuary.spring.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import com.digitalsanctuary.spring.user.persistence.model.PasswordResetToken;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.model.VerificationToken;
import com.digitalsanctuary.spring.user.persistence.repository.PasswordResetTokenRepository;
import com.digitalsanctuary.spring.user.persistence.repository.UserRepository;
import com.digitalsanctuary.spring.user.persistence.repository.VerificationTokenRepository;
import com.digitalsanctuary.spring.user.test.app.TestApplication;
import com.digitalsanctuary.spring.user.test.builders.UserTestDataBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Validates that single-use token consumption is truly atomic under concurrency on a real, production-grade database
 * (not just H2). The fix makes the conditional {@code DELETE} (which returns the affected row count) the atomicity
 * guard: the row lock serializes concurrent deletes, so exactly one caller observes a count of {@code 1} (and applies
 * the effect) while the rest observe {@code 0} (and are rejected). A plain read-check-delete would let two requests
 * both read the token under READ_COMMITTED and both succeed — the replay this test guards against.
 *
 * <p>
 * Two threads race to consume the SAME token at the same instant (released together via a {@link CountDownLatch}).
 * Exactly one must win. This is asserted for both the password-reset consume path
 * ({@link UserService#validateAndConsumePasswordResetToken(String)}) and the email-verification consume path
 * ({@link UserVerificationService#validateVerificationToken(String)}).
 * </p>
 *
 * <p>
 * Subclasses provide a real database via Testcontainers. The class is deliberately NOT {@code @Transactional}: each
 * consume must run in its own service-managed transaction on its own thread, and a test-managed transaction would
 * defeat the race.
 * </p>
 */
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
abstract class AbstractConcurrentTokenConsumeTest {

	@Autowired
	private UserService userService;

	@Autowired
	private UserVerificationService userVerificationService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordResetTokenRepository passwordResetTokenRepository;

	@Autowired
	private VerificationTokenRepository verificationTokenRepository;

	@Autowired
	private TokenHasher tokenHasher;

	@AfterEach
	void cleanUp() {
		// The threads commit their own transactions, so clean up explicitly.
		passwordResetTokenRepository.deleteAll();
		verificationTokenRepository.deleteAll();
		userRepository.deleteAll();
	}

	@RepeatedTest(value = 3, name = "{displayName} [run {currentRepetition}/{totalRepetitions}]")
	@DisplayName("password-reset token is consumed by exactly one of two racing threads")
	void shouldConsumePasswordResetTokenExactlyOnceUnderConcurrency() throws InterruptedException {
		final User user = userRepository.save(UserTestDataBuilder.aUser()
				.withId(null) // let the DB assign the id so save() performs an INSERT, not a merge
				.withEmail("pwd-race-" + System.nanoTime() + "@test.com")
				.withFirstName("Pwd").withLastName("Race").enabled().build());
		final String rawToken = "pwd-reset-" + System.nanoTime();
		passwordResetTokenRepository.save(new PasswordResetToken(tokenHasher.hash(rawToken), user, 60));

		final List<Object> outcomes = raceTwoThreads(() -> userService.validateAndConsumePasswordResetToken(rawToken));

		final long wins = outcomes.stream().filter(o -> o instanceof User).count();
		assertThat(wins).as("exactly one thread may consume the password-reset token").isEqualTo(1);
		assertThat(passwordResetTokenRepository.findByToken(tokenHasher.hash(rawToken)))
				.as("the token must be gone after consumption").isNull();
	}

	@RepeatedTest(value = 3, name = "{displayName} [run {currentRepetition}/{totalRepetitions}]")
	@DisplayName("verification token is consumed by exactly one of two racing threads")
	void shouldConsumeVerificationTokenExactlyOnceUnderConcurrency() throws InterruptedException {
		final User user = userRepository.save(UserTestDataBuilder.aUser()
				.withId(null) // let the DB assign the id so save() performs an INSERT, not a merge
				.withEmail("verify-race-" + System.nanoTime() + "@test.com")
				.withFirstName("Verify").withLastName("Race").unverified().build());
		final String rawToken = "verify-" + System.nanoTime();
		verificationTokenRepository.save(new VerificationToken(tokenHasher.hash(rawToken), user, 60));

		final List<Object> outcomes =
				raceTwoThreads(() -> userVerificationService.validateVerificationToken(rawToken));

		final long valid = outcomes.stream()
				.filter(o -> o == UserService.TokenValidationResult.VALID).count();
		assertThat(valid).as("exactly one thread may validate (consume) the verification token").isEqualTo(1);
		assertThat(verificationTokenRepository.findByToken(tokenHasher.hash(rawToken)))
				.as("the token must be gone after consumption").isNull();
		assertThat(userRepository.findById(user.getId()))
				.get().extracting(User::isEnabled).as("the user is enabled exactly once").isEqualTo(true);
	}

	/**
	 * Runs the given consume action on two threads released simultaneously and returns both outcomes.
	 *
	 * @param consume the consume action under test
	 * @return the two outcomes (a result object, or {@code null} for the losing/rejected call)
	 */
	private List<Object> raceTwoThreads(final Callable<Object> consume) throws InterruptedException {
		final int threadCount = 2;
		final CountDownLatch readyLatch = new CountDownLatch(threadCount);
		final CountDownLatch startLatch = new CountDownLatch(1);
		final ExecutorService executor = Executors.newFixedThreadPool(threadCount);
		try {
			final List<Future<Object>> futures = new ArrayList<>();
			for (int i = 0; i < threadCount; i++) {
				futures.add(executor.submit(() -> {
					readyLatch.countDown();
					if (!startLatch.await(30, TimeUnit.SECONDS)) {
						throw new IllegalStateException("start gate was never opened");
					}
					return consume.call();
				}));
			}

			assertThat(readyLatch.await(30, TimeUnit.SECONDS))
					.as("both consume threads should reach the start gate").isTrue();
			startLatch.countDown();

			final AtomicInteger unexpected = new AtomicInteger();
			final List<Object> outcomes = new ArrayList<>();
			for (Future<Object> future : futures) {
				try {
					outcomes.add(future.get(60, TimeUnit.SECONDS));
				} catch (ExecutionException e) {
					unexpected.incrementAndGet();
				} catch (Exception e) {
					unexpected.incrementAndGet();
				}
			}
			assertThat(unexpected.get()).as("neither consume call should throw").isZero();
			return outcomes;
		} finally {
			executor.shutdownNow();
		}
	}
}
