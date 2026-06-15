package com.digitalsanctuary.spring.user.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import com.digitalsanctuary.spring.user.persistence.model.PasswordHistoryEntry;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.test.annotations.DatabaseTest;
import com.digitalsanctuary.spring.user.test.builders.UserTestDataBuilder;

/**
 * Repository slice tests for {@link PasswordHistoryRepository}, focusing on the bounded, set-based
 * password-history pruning used by {@code UserService.cleanUpPasswordHistory}. These verify that the
 * cutoff-id lookup plus {@code deleteByUserAndIdLessThan} keeps exactly the most recent N entries and
 * is tolerant of being run repeatedly.
 */
@DatabaseTest
class PasswordHistoryRepositoryTest {

	@Autowired
	private PasswordHistoryRepository passwordHistoryRepository;

	@Autowired
	private TestEntityManager entityManager;

	private User persistUser(String email) {
		User user = UserTestDataBuilder.aUser().withId(null).withEmail(email).build();
		return entityManager.persistAndFlush(user);
	}

	private void persistHistoryEntries(User user, int count) {
		LocalDateTime base = LocalDateTime.now();
		for (int i = 0; i < count; i++) {
			// Same timestamp on purpose for some rows, so the test exercises id-based ordering
			PasswordHistoryEntry entry = new PasswordHistoryEntry(user, "hash-" + i, base.plusSeconds(i % 2));
			entityManager.persist(entry);
		}
		entityManager.flush();
	}

	/**
	 * Mirrors the keep-N logic in {@code UserService.cleanUpPasswordHistory}: locate the oldest entry
	 * to keep (0-based index {@code maxEntries - 1}, newest first) and delete everything older.
	 */
	private int prune(User user, int maxEntries) {
		List<Long> cutoffIds = passwordHistoryRepository.findIdsByUserOrderByIdDesc(user, PageRequest.of(maxEntries - 1, 1));
		if (cutoffIds.isEmpty()) {
			return 0;
		}
		return passwordHistoryRepository.deleteByUserAndIdLessThan(user, cutoffIds.get(0));
	}

	@Test
	void prune_keepsOnlyMostRecentNEntries() {
		User user = persistUser("prune-keep-n@test.com");
		persistHistoryEntries(user, 10);
		int maxEntries = 5;

		int deleted = prune(user, maxEntries);

		assertThat(deleted).isEqualTo(5);
		List<PasswordHistoryEntry> remaining = passwordHistoryRepository.findByUserOrderByEntryDateDesc(user);
		assertThat(remaining).hasSize(maxEntries);
		// The kept entries are the most recent ones (highest ids): hash-5..hash-9
		assertThat(remaining).extracting(PasswordHistoryEntry::getPasswordHash)
				.containsExactlyInAnyOrder("hash-5", "hash-6", "hash-7", "hash-8", "hash-9");
	}

	@Test
	void prune_isNoOpWhenAtOrBelowLimit() {
		User user = persistUser("prune-under-limit@test.com");
		persistHistoryEntries(user, 3);
		int maxEntries = 5;

		int deleted = prune(user, maxEntries);

		assertThat(deleted).isZero();
		assertThat(passwordHistoryRepository.findByUserOrderByEntryDateDesc(user)).hasSize(3);
	}

	@Test
	void prune_isIdempotentWhenCalledRepeatedly() {
		User user = persistUser("prune-idempotent@test.com");
		persistHistoryEntries(user, 8);
		int maxEntries = 4;

		int firstDeleted = prune(user, maxEntries);
		int secondDeleted = prune(user, maxEntries);
		int thirdDeleted = prune(user, maxEntries);

		assertThat(firstDeleted).isEqualTo(4);
		assertThat(secondDeleted).isZero();
		assertThat(thirdDeleted).isZero();
		assertThat(passwordHistoryRepository.findByUserOrderByEntryDateDesc(user)).hasSize(maxEntries);
	}

	@Test
	void prune_onlyAffectsTargetUser() {
		User target = persistUser("prune-target@test.com");
		User other = persistUser("prune-other@test.com");
		persistHistoryEntries(target, 6);
		persistHistoryEntries(other, 6);

		int deleted = prune(target, 2);

		assertThat(deleted).isEqualTo(4);
		assertThat(passwordHistoryRepository.findByUserOrderByEntryDateDesc(target)).hasSize(2);
		// The other user's history is untouched
		assertThat(passwordHistoryRepository.findByUserOrderByEntryDateDesc(other)).hasSize(6);
	}
}
