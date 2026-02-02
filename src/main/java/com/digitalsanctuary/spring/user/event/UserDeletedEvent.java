package com.digitalsanctuary.spring.user.event;

import org.springframework.context.ApplicationEvent;

/**
 * Event published after a user entity has been successfully deleted.
 *
 * <p>Unlike {@link UserPreDeleteEvent} which is published before deletion and allows
 * cleanup operations within the transaction, this event is published after the
 * deletion has been committed. Use this event for post-deletion notifications,
 * external system updates, or logging that should only occur after successful deletion.
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
