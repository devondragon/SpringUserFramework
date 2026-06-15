package com.digitalsanctuary.spring.user.gdpr;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.digitalsanctuary.spring.user.event.UserDeletedEvent;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.repository.UserRepository;
import com.digitalsanctuary.spring.user.test.app.TestApplication;
import com.digitalsanctuary.spring.user.test.builders.UserTestDataBuilder;

/**
 * Integration test proving that {@link GdprDeletionService} actually runs the deletion inside a transaction and
 * publishes {@link UserDeletedEvent} only <em>after that transaction commits</em>. This is the integration-level
 * proof for the self-invocation fix: {@code deleteUser} now invokes {@code executeUserDeletion} through the Spring
 * proxy ({@code self}), so the {@code @Transactional} boundary is honored. A unit test cannot verify this — the
 * transactional proxy only exists in a real context.
 *
 * <p>
 * This test is deliberately <strong>not</strong> {@code @Transactional}: the service must run and commit (or roll
 * back) its own transaction so the after-commit synchronization fires for real. Two behaviors are asserted:
 * </p>
 * <ul>
 * <li><b>Happy path:</b> the user row is committed (gone) by the time the event listener runs, and no transaction is
 * active during event delivery — i.e. the event is genuinely after-commit, not mid-transaction.</li>
 * <li><b>Rollback path:</b> when a data contributor throws, the whole deletion rolls back (the user still exists) and
 * <em>no</em> {@link UserDeletedEvent} is published — proving the event is not emitted on a failed/partial delete.</li>
 * </ul>
 */
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
@Import(GdprDeletionAfterCommitIntegrationTest.TestBeans.class)
@DisplayName("GdprDeletionService after-commit / rollback integration")
class GdprDeletionAfterCommitIntegrationTest {

    @Autowired
    private GdprDeletionService gdprDeletionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DeletedEventRecorder recorder;

    @Autowired
    private TogglableFailingContributor failingContributor;

    @AfterEach
    void cleanUp() {
        // No test-managed transaction here, so clean up committed rows and reset shared test state.
        userRepository.deleteAll();
        recorder.reset();
        failingContributor.disarm();
    }

    @Test
    @DisplayName("publishes UserDeletedEvent AFTER the deletion transaction commits")
    void publishesEventAfterCommit() {
        User user = userRepository.save(UserTestDataBuilder.aUser()
                .withId(null) // let the DB assign the id so save() performs an INSERT, not a merge
                .withEmail("after-commit-" + System.nanoTime() + "@test.com")
                .withFirstName("After").withLastName("Commit").enabled().build());
        Long userId = user.getId();

        GdprDeletionService.DeletionResult result = gdprDeletionService.deleteUser(user, false);

        assertThat(result.isSuccess()).as("deletion should succeed").isTrue();
        assertThat(recorder.isReceived()).as("UserDeletedEvent should be published").isTrue();
        // The whole point of the fix: by the time the event fires, the delete has COMMITTED.
        assertThat(recorder.wasUserAbsentAtEventTime())
                .as("user row must already be deleted when the event is delivered").isTrue();
        // Distinguishes the fix from the bug: the event is delivered from within the transaction's afterCommit
        // synchronization (synchronization active). In the broken self-invocation version there was no transaction,
        // so the event published immediately with NO synchronization active and this would be false.
        assertThat(recorder.wasSynchronizationActiveAtEventTime())
                .as("event must be delivered inside the transaction's afterCommit synchronization").isTrue();
        assertThat(userRepository.findById(userId)).as("user is deleted").isEmpty();
    }

    @Test
    @DisplayName("rolls back the deletion and publishes NO event when a contributor fails")
    void rollsBackAndPublishesNoEventOnFailure() {
        failingContributor.arm();
        User user = userRepository.save(UserTestDataBuilder.aUser()
                .withId(null) // let the DB assign the id so save() performs an INSERT, not a merge
                .withEmail("rollback-" + System.nanoTime() + "@test.com")
                .withFirstName("Roll").withLastName("Back").enabled().build());
        Long userId = user.getId();

        GdprDeletionService.DeletionResult result = gdprDeletionService.deleteUser(user, false);

        assertThat(result.isSuccess()).as("deletion should fail when a contributor throws").isFalse();
        assertThat(recorder.isReceived())
                .as("no UserDeletedEvent may be published when the transaction rolled back").isFalse();
        assertThat(userRepository.findById(userId))
                .as("the user must still exist — the deletion transaction rolled back atomically").isPresent();
    }

    @TestConfiguration
    static class TestBeans {
        @Bean
        DeletedEventRecorder deletedEventRecorder(UserRepository userRepository) {
            return new DeletedEventRecorder(userRepository);
        }

        @Bean
        TogglableFailingContributor togglableFailingContributor() {
            return new TogglableFailingContributor();
        }
    }

    /**
     * Records receipt of {@link UserDeletedEvent} and captures, at event-delivery time, whether the user row is
     * already gone (committed) and whether a transaction is still active. Used to prove after-commit semantics.
     */
    static class DeletedEventRecorder {
        private final UserRepository userRepository;
        private volatile boolean received;
        private volatile boolean userAbsentAtEventTime;
        private volatile boolean synchronizationActiveAtEventTime;

        DeletedEventRecorder(UserRepository userRepository) {
            this.userRepository = userRepository;
        }

        @EventListener
        void onUserDeleted(UserDeletedEvent event) {
            received = true;
            synchronizationActiveAtEventTime = TransactionSynchronizationManager.isSynchronizationActive();
            userAbsentAtEventTime = userRepository.findById(event.getUserId()).isEmpty();
        }

        boolean isReceived() {
            return received;
        }

        boolean wasUserAbsentAtEventTime() {
            return userAbsentAtEventTime;
        }

        boolean wasSynchronizationActiveAtEventTime() {
            return synchronizationActiveAtEventTime;
        }

        void reset() {
            received = false;
            userAbsentAtEventTime = false;
            synchronizationActiveAtEventTime = false;
        }
    }

    /**
     * A {@link GdprDataContributor} that throws during {@code prepareForDeletion} only when armed, used to force a
     * transaction rollback in the middle of the deletion.
     */
    static class TogglableFailingContributor implements GdprDataContributor {
        private final AtomicBoolean armed = new AtomicBoolean(false);

        void arm() {
            armed.set(true);
        }

        void disarm() {
            armed.set(false);
        }

        @Override
        public String getDataKey() {
            return "test-failing-contributor";
        }

        @Override
        public Map<String, Object> exportUserData(User user) {
            return Map.of();
        }

        @Override
        public void prepareForDeletion(User user) {
            if (armed.get()) {
                throw new RuntimeException("Simulated contributor failure to force rollback");
            }
        }
    }
}
