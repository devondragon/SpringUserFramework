package com.digitalsanctuary.spring.user.persistence.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * The PasswordHistoryEntry Entity.
 * Stores password hashes for a user to enforce password history policies.
 */
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
@Entity
@Table(name = "password_history_entry", indexes = {
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_entry_date", columnList = "entry_date")
})
public class PasswordHistoryEntry {

    /** The id. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // Identity-based equality keys on this id only. Two transient (unsaved, id == null) instances compare equal,
    // so never use unsaved entities as Set/Map keys; add them only after they have been persisted. See EntityEqualityTest.
    @EqualsAndHashCode.Include
    private Long id;

    /** The user associated with this password entry. */
    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** The hashed password. */
    @ToString.Exclude
    @Column(length = 255, nullable = false)
    private String passwordHash;

    /** The timestamp when the password was stored. */
    @Column(name = "entry_date", nullable = false)
    private LocalDateTime entryDate;

    /**
     * Instantiates a new password history entry.
     */
    public PasswordHistoryEntry() {
        super();
    }

    /**
     * Instantiates a new password history entry with all fields.
     *
     * @param user         the user
     * @param passwordHash the password hash
     * @param entryDate    the entry date
     */
    public PasswordHistoryEntry(final User user, final String passwordHash, final LocalDateTime entryDate) {
        this.user = user;
        this.passwordHash = passwordHash;
        this.entryDate = entryDate;
    }
}
