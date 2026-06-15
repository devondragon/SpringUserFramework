package com.digitalsanctuary.spring.user.event;

import org.springframework.context.ApplicationEvent;

/**
 * Event published before a user entity is deleted. This event can be used to perform any necessary actions or checks before the deletion occurs.
 *
 * <p>
 * This event is typically used in conjunction with an event listener that can handle the pre-deletion logic, such as logging, validation, or
 * cascading deletions.
 * </p>
 *
 * <p>
 * As of 5.0.0 this event no longer carries a live JPA {@code User} entity. Instead it exposes immutable scalar data
 * ({@code userId}, {@code userEmail}) captured at publish time. This prevents detached-entity /
 * {@code LazyInitializationException} hazards when the event is consumed across threads. If a listener needs the full
 * {@code User}, it should load it by {@code userId} from {@code UserRepository} inside its own transaction.
 * </p>
 */
public class UserPreDeleteEvent extends ApplicationEvent {

    private static final long serialVersionUID = 1L;

    /**
     * The ID of the user that is about to be deleted.
     */
    private final Long userId;

    /**
     * The email of the user that is about to be deleted.
     */
    private final String userEmail;

    /**
     * Create a new UserPreDeleteEvent.
     *
     * @param source The object on which the event initially occurred (never {@code null})
     * @param userId The ID of the user that is about to be deleted (never {@code null})
     * @param userEmail The email of the user that is about to be deleted
     */
    public UserPreDeleteEvent(Object source, Long userId, String userEmail) {
        super(source);
        this.userId = userId;
        this.userEmail = userEmail;
    }

    /**
     * Get the ID of the user that is about to be deleted.
     *
     * @return The ID of the user (never {@code null})
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * Get the email of the user that is about to be deleted.
     *
     * @return The email of the user
     */
    public String getUserEmail() {
        return userEmail;
    }

}
