package com.digitalsanctuary.spring.user.event;

import org.springframework.context.ApplicationEvent;
import com.digitalsanctuary.spring.user.gdpr.ConsentRecord;
import com.digitalsanctuary.spring.user.gdpr.ConsentType;
import com.digitalsanctuary.spring.user.persistence.model.User;

/**
 * Event published when a user's consent status changes.
 *
 * <p>This event is published when consent is granted or withdrawn through
 * the {@link com.digitalsanctuary.spring.user.gdpr.ConsentAuditService}.
 * Listeners can use this event to trigger additional actions like
 * updating mailing lists, disabling features, or synchronizing with
 * external consent management systems.
 *
 * @see ConsentRecord
 * @see ConsentType
 * @see com.digitalsanctuary.spring.user.gdpr.ConsentAuditService
 */
public class ConsentChangedEvent extends ApplicationEvent {

    private static final long serialVersionUID = 1L;

    /**
     * The type of consent change.
     */
    public enum ChangeType {
        /**
         * Consent was granted.
         */
        GRANTED,

        /**
         * Consent was withdrawn.
         */
        WITHDRAWN
    }

    /**
     * The user whose consent changed.
     */
    private final User user;

    /**
     * The consent record with details of the change.
     */
    private final ConsentRecord consentRecord;

    /**
     * The type of change (granted or withdrawn).
     */
    private final ChangeType changeType;

    /**
     * Creates a new ConsentChangedEvent.
     *
     * @param source the object on which the event initially occurred
     * @param user the user whose consent changed
     * @param consentRecord the consent record with details
     * @param changeType whether consent was granted or withdrawn
     */
    public ConsentChangedEvent(Object source, User user, ConsentRecord consentRecord, ChangeType changeType) {
        super(source);
        this.user = user;
        this.consentRecord = consentRecord;
        this.changeType = changeType;
    }

    /**
     * Gets the user whose consent changed.
     *
     * @return the user
     */
    public User getUser() {
        return user;
    }

    /**
     * Gets the ID of the user whose consent changed.
     *
     * @return the user ID
     */
    public Long getUserId() {
        return user != null ? user.getId() : null;
    }

    /**
     * Gets the consent record with details of the change.
     *
     * @return the consent record
     */
    public ConsentRecord getConsentRecord() {
        return consentRecord;
    }

    /**
     * Gets the type of consent that changed.
     *
     * @return the consent type
     */
    public ConsentType getConsentType() {
        return consentRecord != null ? consentRecord.getType() : null;
    }

    /**
     * Gets whether consent was granted or withdrawn.
     *
     * @return the change type
     */
    public ChangeType getChangeType() {
        return changeType;
    }

    /**
     * Convenience method to check if consent was granted.
     *
     * @return true if consent was granted
     */
    public boolean isGranted() {
        return changeType == ChangeType.GRANTED;
    }

    /**
     * Convenience method to check if consent was withdrawn.
     *
     * @return true if consent was withdrawn
     */
    public boolean isWithdrawn() {
        return changeType == ChangeType.WITHDRAWN;
    }

}
