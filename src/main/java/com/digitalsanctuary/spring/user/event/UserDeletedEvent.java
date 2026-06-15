package com.digitalsanctuary.spring.user.event;

import org.springframework.context.ApplicationEvent;

/**
 * Event published after a user entity has been successfully deleted.
 *
 * <p>Unlike {@link UserPreDeleteEvent} which is published before deletion and allows
 * cleanup operations within the transaction, this event is delivered <strong>after the
 * deletion transaction has committed</strong>. Listeners (including {@code @Async} ones)
 * are therefore guaranteed to observe a committed deletion and will never act on a
 * not-yet-committed change. Use this event for post-deletion notifications, external
 * system updates, or logging that should only occur after successful deletion.
 *
 * <p>Delivery-after-commit is achieved by the publisher itself, not by the listener:
 * the event is published from a registered {@code TransactionSynchronization.afterCommit}
 * callback, so it is only fired once the surrounding transaction has committed. Because
 * publication is already deferred, consumers do <strong>not</strong> need
 * {@code @TransactionalEventListener} &mdash; a plain {@code @EventListener} (or an
 * {@code @Async @EventListener}) will already receive the event post-commit. When no
 * transaction synchronization is active (e.g. a non-transactional caller), the event is
 * published immediately as a fallback. {@code GdprDeletionService.executeUserDeletion}
 * uses this same deferred-publication mechanism.
 *
 * <p>Note: Since the user entity has been deleted by the time this event is published,
 * only the user's ID and email are retained in this event.
 *
 * @see UserPreDeleteEvent
 * @see com.digitalsanctuary.spring.user.gdpr.GdprDeletionService
 */
public class UserDeletedEvent extends ApplicationEvent {

    private static final long serialVersionUID = 1L;

    /**
     * The ID of the deleted user.
     */
    private final Long userId;

    /**
     * The email of the deleted user.
     */
    private final String userEmail;

    /**
     * Whether data was exported before deletion.
     */
    private final boolean dataExported;

    /**
     * Creates a new UserDeletedEvent.
     *
     * @param source the object on which the event initially occurred
     * @param userId the ID of the deleted user
     * @param userEmail the email of the deleted user
     * @param dataExported whether data was exported before deletion
     */
    public UserDeletedEvent(Object source, Long userId, String userEmail, boolean dataExported) {
        super(source);
        this.userId = userId;
        this.userEmail = userEmail;
        this.dataExported = dataExported;
    }

    /**
     * Creates a new UserDeletedEvent without export flag.
     *
     * @param source the object on which the event initially occurred
     * @param userId the ID of the deleted user
     * @param userEmail the email of the deleted user
     */
    public UserDeletedEvent(Object source, Long userId, String userEmail) {
        this(source, userId, userEmail, false);
    }

    /**
     * Gets the ID of the deleted user.
     *
     * @return the user ID
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * Gets the email of the deleted user.
     *
     * @return the user email
     */
    public String getUserEmail() {
        return userEmail;
    }

    /**
     * Returns whether data was exported before deletion.
     *
     * @return true if data was exported
     */
    public boolean isDataExported() {
        return dataExported;
    }

}
