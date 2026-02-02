package com.digitalsanctuary.spring.user.event;

import org.springframework.context.ApplicationEvent;
import com.digitalsanctuary.spring.user.dto.GdprExportDTO;
import com.digitalsanctuary.spring.user.persistence.model.User;

/**
 * Event published after a user's data has been successfully exported for GDPR compliance.
 *
 * <p>This event can be used by listeners to perform additional actions after data export,
 * such as logging, notification, or triggering downstream processes.
 *
 * @see User
 * @see GdprExportDTO
 * @see com.digitalsanctuary.spring.user.gdpr.GdprExportService
 */
public class UserDataExportedEvent extends ApplicationEvent {

    private static final long serialVersionUID = 1L;

    /**
     * The user whose data was exported.
     */
    private final User user;

    /**
     * The exported data (may be null if not retained in the event).
     */
    private final GdprExportDTO exportedData;

    /**
     * Creates a new UserDataExportedEvent.
     *
     * @param source the object on which the event initially occurred
     * @param user the user whose data was exported
     * @param exportedData the exported data
     */
    public UserDataExportedEvent(Object source, User user, GdprExportDTO exportedData) {
        super(source);
        this.user = user;
        this.exportedData = exportedData;
    }

    /**
     * Creates a new UserDataExportedEvent without retaining the export data.
     *
     * @param source the object on which the event initially occurred
     * @param user the user whose data was exported
     */
    public UserDataExportedEvent(Object source, User user) {
        this(source, user, null);
    }

    /**
     * Gets the user whose data was exported.
     *
     * @return the user
     */
    public User getUser() {
        return user;
    }

    /**
     * Gets the ID of the user whose data was exported.
     *
     * @return the user ID
     */
    public Long getUserId() {
        return user != null ? user.getId() : null;
    }

    /**
     * Gets the email of the user whose data was exported.
     *
     * @return the user email
     */
    public String getUserEmail() {
        return user != null ? user.getEmail() : null;
    }

    /**
     * Gets the exported data, if retained in the event.
     *
     * @return the exported data, or null if not retained
     */
    public GdprExportDTO getExportedData() {
        return exportedData;
    }

}
