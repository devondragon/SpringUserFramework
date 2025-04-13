package com.digitalsanctuary.spring.user.event;

import org.springframework.context.ApplicationEvent;
import com.digitalsanctuary.spring.user.persistence.model.User;

/**
 * Event published before a user entity is deleted. This event can be used to perform any necessary actions or checks before the deletion occurs.
 *
 * <p>
 * This event is typically used in conjunction with an event listener that can handle the pre-deletion logic, such as logging, validation, or
 * cascading deletions.
 * </p>
 *
 * @see User
 */
public class UserPreDeleteEvent extends ApplicationEvent {

    /**
     * The user entity that is about to be deleted.
     */
    private final User user;

    /**
     * Create a new UserDeleteEvent.
     *
     * @param source The object on which the event initially occurred (never {@code null})
     * @param user The user entity that is about to be deleted (never {@code null})
     */
    public UserPreDeleteEvent(Object source, User user) {
        super(source);
        this.user = user;
    }

    /**
     * Get the user entity that is about to be deleted.
     *
     * @return The user entity (never {@code null})
     */
    public User getUser() {
        return user;
    }

    /**
     * Get the ID of the user entity that is about to be deleted.
     *
     * @return The ID of the user entity (never {@code null})
     */
    public Long getUserId() {
        return user.getId();
    }

}
