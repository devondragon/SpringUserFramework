package com.digitalsanctuary.spring.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import com.digitalsanctuary.spring.user.dto.UserDto;
import com.digitalsanctuary.spring.user.exceptions.UserAlreadyExistException;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.repository.UserRepository;
import com.digitalsanctuary.spring.user.test.app.TestApplication;
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
 * Validates that the SERIALIZABLE duplicate-registration race protection (UserService.registerNewUserAccount ->
 * persistNewUserAccount, isolation = SERIALIZABLE, with DataIntegrityViolationException / ConcurrencyFailureException
 * translated to UserAlreadyExistException) actually holds on a real, production-grade database — not just on H2.
 *
 * <p>
 * Two threads race to register the SAME email at the same instant (released together via a CountDownLatch). On a real
 * Postgres (SSI) or MariaDB/InnoDB (next-key locks) under SERIALIZABLE plus the unique email constraint, exactly one
 * thread must win (one persisted User) and the other must fail with the handled, translated
 * {@link UserAlreadyExistException} — never a raw serialization/constraint exception bubbling up as a 500, and never a
 * second user row.
 * </p>
 *
 * <p>
 * Subclasses provide a real database via Testcontainers and point {@code spring.datasource.*} at it via
 * {@code @DynamicPropertySource}. The {@code test} profile is active so {@code RolePrivilegeSetupService} seeds the
 * {@code ROLE_USER} role on context refresh (the registration path requires it). This class deliberately is NOT
 * {@code @Transactional}: each registration must run in its own service-managed transaction on its own thread, and a
 * test-managed transaction would defeat that.
 * </p>
 */
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
abstract class AbstractConcurrentRegistrationTest {

	/** Password that satisfies upper/lower/digit/special policy requirements. */
	private static final String VALID_PASSWORD = "Str0ng!Passw0rd#2024";

	@Autowired
	private UserService userService;

	@Autowired
	private UserRepository userRepository;

	@AfterEach
	void cleanUp() {
		// No @Transactional rollback here (the threads commit their own transactions), so clean up explicitly.
		userRepository.deleteAll();
	}

	@RepeatedTest(value = 3, name = "{displayName} [run {currentRepetition}/{totalRepetitions}]")
	@DisplayName("should serialize concurrent duplicate registration into exactly one user and one UserAlreadyExistException")
	void shouldSerializeConcurrentDuplicateRegistrationWhenTwoThreadsRaceSameEmail() throws InterruptedException {
		final String email = "race-" + System.nanoTime() + "@test.com";

		final int threadCount = 2;
		final CountDownLatch readyLatch = new CountDownLatch(threadCount);
		final CountDownLatch startLatch = new CountDownLatch(1);
		final ExecutorService executor = Executors.newFixedThreadPool(threadCount);

		try {
			final List<Future<RegistrationOutcome>> futures = new ArrayList<>();
			for (int i = 0; i < threadCount; i++) {
				futures.add(executor.submit(registrationTask(email, readyLatch, startLatch)));
			}

			// Wait until both threads are parked at the start gate, then release them simultaneously
			// to maximize the registration race.
			assertThat(readyLatch.await(30, TimeUnit.SECONDS))
					.as("both registration threads should reach the start gate")
					.isTrue();
			startLatch.countDown();

			final AtomicInteger successCount = new AtomicInteger();
			final AtomicInteger alreadyExistCount = new AtomicInteger();
			final List<Throwable> unexpectedFailures = new ArrayList<>();
			final List<User> persistedUsers = new ArrayList<>();

			for (Future<RegistrationOutcome> future : futures) {
				final RegistrationOutcome outcome = collect(future);
				if (outcome.user != null) {
					successCount.incrementAndGet();
					persistedUsers.add(outcome.user);
				} else if (outcome.error instanceof UserAlreadyExistException) {
					alreadyExistCount.incrementAndGet();
				} else {
					unexpectedFailures.add(outcome.error);
				}
			}

			assertThat(unexpectedFailures)
					.as("neither thread should fail with a raw serialization/constraint exception (it must be "
							+ "translated to UserAlreadyExistException)")
					.isEmpty();
			assertThat(successCount.get())
					.as("exactly one thread should successfully register the user")
					.isEqualTo(1);
			assertThat(alreadyExistCount.get())
					.as("the losing thread should fail with the handled UserAlreadyExistException")
					.isEqualTo(1);

			final User found = userRepository.findByEmail(email.toLowerCase());
			assertThat(found)
					.as("exactly one user row should exist for the raced email")
					.isNotNull();

			final long rowCount = userRepository.findAll().stream()
					.filter(u -> email.toLowerCase().equals(u.getEmail()))
					.count();
			assertThat(rowCount)
					.as("the database must contain EXACTLY ONE user row for the raced email — two rows would mean "
							+ "SERIALIZABLE failed to prevent the duplicate")
					.isEqualTo(1);
		} finally {
			executor.shutdownNow();
		}
	}

	private Callable<RegistrationOutcome> registrationTask(final String email, final CountDownLatch readyLatch,
			final CountDownLatch startLatch) {
		return () -> {
			final UserDto dto = new UserDto();
			dto.setFirstName("Race");
			dto.setLastName("Condition");
			dto.setEmail(email);
			dto.setPassword(VALID_PASSWORD);
			dto.setMatchingPassword(VALID_PASSWORD);

			readyLatch.countDown();
			if (!startLatch.await(30, TimeUnit.SECONDS)) {
				throw new IllegalStateException("start gate was never opened");
			}

			try {
				return RegistrationOutcome.success(userService.registerNewUserAccount(dto));
			} catch (Throwable t) {
				return RegistrationOutcome.failure(t);
			}
		};
	}

	private RegistrationOutcome collect(final Future<RegistrationOutcome> future) {
		try {
			return future.get(60, TimeUnit.SECONDS);
		} catch (ExecutionException e) {
			// The task catches everything and returns an outcome, so a raw ExecutionException is itself unexpected.
			return RegistrationOutcome.failure(e.getCause() != null ? e.getCause() : e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("interrupted while collecting registration outcome", e);
		} catch (Exception e) {
			return RegistrationOutcome.failure(e);
		}
	}

	/** Captures the result of a single registration attempt: either the persisted user or the thrown error. */
	private static final class RegistrationOutcome {
		private final User user;
		private final Throwable error;

		private RegistrationOutcome(final User user, final Throwable error) {
			this.user = user;
			this.error = error;
		}

		static RegistrationOutcome success(final User user) {
			return new RegistrationOutcome(user, null);
		}

		static RegistrationOutcome failure(final Throwable error) {
			return new RegistrationOutcome(null, error);
		}
	}
}
