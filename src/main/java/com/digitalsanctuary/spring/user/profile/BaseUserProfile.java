package com.digitalsanctuary.spring.user.profile;

import java.time.LocalDateTime;
import com.digitalsanctuary.spring.user.persistence.model.User;
import jakarta.persistence.Column;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import lombok.Data;

/**
 * Base class for user profile entities that extend the core {@link User} functionality. This class provides the foundation for creating
 * application-specific user profiles with shared common attributes.
 *
 * <p>
 * This class uses {@code @MappedSuperclass} to allow inheritance of JPA mappings, enabling extending classes to add additional fields while
 * maintaining a consistent database structure. The profile shares its primary key with the associated {@link User} entity through the {@code @MapsId}
 * annotation.
 * </p>
 *
 * Example implementation: {@code @Entity
 *
 * @Table(name = "customer_profile") public class CustomerProfile extends BaseUserProfile { private String customerType; private String
 * shippingPreference; private List<Order> orders; } }
 *
 * Database Structure: - id/user_id (PK/FK to user_account table) - last_accessed (timestamp) - preferred_locale (varchar)
 *
 * @see User
 * @see MappedSuperclass
 */
@Data
@MappedSuperclass
public abstract class BaseUserProfile {

    /**
     * The primary key for the profile, shared with the associated User entity. This is automatically populated through the {@code @MapsId} annotation
     * when the profile is persisted.
     */
    @Id
    private Long id;

    /**
     * The associated User entity. This establishes a one-to-one relationship with shared primary key through the {@code @MapsId} annotation. The
     * foreign key column will be named "user_id".
     */
    @OneToOne
    @MapsId
    @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "fk_user"))
    private User user;

    /**
     * Timestamp of the last time this profile was accessed. This can be used for tracking user activity, session management, or implementing timeout
     * functionality.
     */
    @Column(name = "last_accessed")
    private LocalDateTime lastAccessed;

    /**
     * The user's preferred locale for internationalization purposes. This should contain a valid locale code (e.g., "en_US", "fr_FR"). Applications
     * can use this to provide localized content and formatting.
     */
    @Column(name = "preferred_locale")
    private String locale;


}
