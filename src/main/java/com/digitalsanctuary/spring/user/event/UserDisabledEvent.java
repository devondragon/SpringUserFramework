package com.digitalsanctuary.spring.user.event;

import org.springframework.context.ApplicationEvent;

/**
 * Event published after a user account has been disabled (soft-deleted).
 *
 * <p>When {@code user.actuallyDeleteAccount} is {@code false} (the default), an account
 * "deletion" request disables the user rather than removing the row. This event makes that
 * default soft-delete path observable so consuming applications can react (e.g. revoke
 * external access, notify downstream systems, or update analytics).
 *
 * <p>Like {@link UserDeletedEvent}, this event is delivered <strong>after the disabling
 * transaction has committed</strong>. Listeners (including {@code @Async} ones) are therefore
 * guaranteed to observe a committed change and will never act on a not-yet-committed update.
 * Delivery-after-commit is achieved by the publisher itself: the event is published from a
 * registered {@code TransactionSynchronization.afterCommit} callback. Because publication is
 * already deferred, consumers do <strong>not</strong> need {@code @TransactionalEventListener}
 * &mdash; a plain {@code @EventListener} (or an {@code @Async @EventListener}) will already
 * receive the event post-commit. When no transaction synchronization is active (e.g. a
 * non-transactional caller), the event is published immediately as a fallback.
 *
 * <p>Note: To mirror {@link UserDeletedEvent} and avoid handing listeners a live, detached, or
 * mutated entity, only the user's ID and email are retained in this event.
 *
 * @see UserDeletedEvent
 * @see UserPreDeleteEvent
 */
public class UserDisabledEvent extends ApplicationEvent {

    private static final long serialVersionUID = 1L;

    /**
     * The ID of the disabled user.
     */
    private final Long userId;

    /**
     * The email of the disabled user.
     */
    private final String userEmail;

    /**
     * Creates a new UserDisabledEvent.
     *
     * @param source the object on which the event initially occurred
     * @param userId the ID of the disabled user
     * @param userEmail the email of the disabled user
     */
    public UserDisabledEvent(Object source, Long userId, String userEmail) {
        super(source);
        this.userId = userId;
        this.userEmail = userEmail;
    }

    /**
     * Gets the ID of the disabled user.
     *
     * @return the user ID
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * Gets the email of the disabled user.
     *
     * @return the user email
     */
    public String getUserEmail() {
        return userEmail;
    }

}
