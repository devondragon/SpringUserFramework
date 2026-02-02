package com.digitalsanctuary.spring.user.gdpr;

import java.util.Map;
import com.digitalsanctuary.spring.user.persistence.model.User;

/**
 * Interface for components that contribute user data to GDPR exports.
 *
 * <p>Consuming applications can implement this interface to include their
 * own domain-specific data in GDPR data exports. The framework automatically
 * discovers all beans implementing this interface and aggregates their data
 * during export operations.
 *
 * <p>Example implementation:
 * <pre>{@code
 * @Component
 * public class OrderDataContributor implements GdprDataContributor {
 *
 *     private final OrderRepository orderRepository;
 *
 *     @Override
 *     public String getDataKey() {
 *         return "orders";
 *     }
 *
 *     @Override
 *     public Map<String, Object> exportUserData(User user) {
 *         List<Order> orders = orderRepository.findByUserId(user.getId());
 *         return Map.of(
 *             "count", orders.size(),
 *             "orders", orders.stream().map(this::toExportFormat).toList()
 *         );
 *     }
 *
 *     @Override
 *     public void prepareForDeletion(User user) {
 *         // Delete or anonymize order records
 *         orderRepository.anonymizeByUserId(user.getId());
 *     }
 * }
 * }</pre>
 *
 * @see GdprExportService
 * @see GdprDeletionService
 */
public interface GdprDataContributor {

    /**
     * Returns a unique key identifying this data section in the export.
     *
     * <p>The key should be a simple, descriptive identifier that clearly
     * indicates what data this contributor provides (e.g., "orders",
     * "preferences", "activity_log").
     *
     * @return a unique data section key
     */
    String getDataKey();

    /**
     * Exports GDPR-relevant data for the given user.
     *
     * <p>Returns a map containing the user's data managed by this contributor.
     * The map structure should be suitable for JSON serialization.
     * Return null or an empty map if no data exists for this user.
     *
     * <p>This method should include all data that:
     * <ul>
     *   <li>Is directly linked to the user's identity</li>
     *   <li>Could be used to identify the user</li>
     *   <li>The user has a right to access under GDPR Article 15</li>
     * </ul>
     *
     * @param user the user whose data to export
     * @return a map of exportable data, or null/empty if no data exists
     */
    Map<String, Object> exportUserData(User user);

    /**
     * Called before user deletion to clean up related data.
     *
     * <p>The framework handles deletion of the User entity and related
     * framework-managed data. Implementers should handle cleanup of their
     * own domain-specific tables and data.
     *
     * <p>This method is called within the deletion transaction, so any
     * exceptions will cause the entire deletion to roll back.
     *
     * <p>Implementations should either:
     * <ul>
     *   <li>Delete all user-related data (for full data erasure)</li>
     *   <li>Anonymize data that must be retained for legal/business reasons</li>
     * </ul>
     *
     * @param user the user being deleted
     */
    default void prepareForDeletion(User user) {
        // Default no-op; implementations can override to clean up their data
    }

}
