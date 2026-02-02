package com.digitalsanctuary.spring.user.gdpr;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.digitalsanctuary.spring.user.dto.GdprExportDTO;
import com.digitalsanctuary.spring.user.event.UserDeletedEvent;
import com.digitalsanctuary.spring.user.event.UserPreDeleteEvent;
import com.digitalsanctuary.spring.user.persistence.model.PasswordResetToken;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.model.VerificationToken;
import com.digitalsanctuary.spring.user.persistence.repository.PasswordResetTokenRepository;
import com.digitalsanctuary.spring.user.persistence.repository.UserRepository;
import com.digitalsanctuary.spring.user.persistence.repository.VerificationTokenRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for handling GDPR-compliant user deletion (Right to be Forgotten).
 *
 * <p>This service orchestrates the complete user deletion process including:
 * <ul>
 *   <li>Optional data export before deletion</li>
 *   <li>Notifying all {@link GdprDataContributor} beans to clean up their data</li>
 *   <li>Publishing {@link UserPreDeleteEvent} for additional cleanup</li>
 *   <li>Deleting framework-managed data (user, tokens)</li>
 *   <li>Publishing {@link UserDeletedEvent} after successful deletion</li>
 * </ul>
 *
 * @see GdprDataContributor
 * @see GdprExportService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GdprDeletionService {

    private final GdprConfig gdprConfig;
    private final GdprExportService gdprExportService;
    private final UserRepository userRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final List<GdprDataContributor> dataContributors;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Result of a GDPR deletion operation.
     */
    public static class DeletionResult {
        private final boolean success;
        private final GdprExportDTO exportedData;
        private final String message;

        private DeletionResult(boolean success, GdprExportDTO exportedData, String message) {
            this.success = success;
            this.exportedData = exportedData;
            this.message = message;
        }

        public static DeletionResult success(GdprExportDTO exportedData) {
            return new DeletionResult(true, exportedData, "User data deleted successfully");
        }

        public static DeletionResult successWithExport(GdprExportDTO exportedData) {
            return new DeletionResult(true, exportedData, "User data exported and deleted successfully");
        }

        public static DeletionResult failure(String message) {
            return new DeletionResult(false, null, message);
        }

        public boolean isSuccess() {
            return success;
        }

        public GdprExportDTO getExportedData() {
            return exportedData;
        }

        public String getMessage() {
            return message;
        }
    }

    /**
     * Deletes a user and all associated data in a GDPR-compliant manner.
     *
     * <p>If {@code exportBeforeDeletion} is enabled in configuration, the user's
     * data is exported and included in the result before deletion.
     *
     * <p>Note: Export is performed OUTSIDE the transaction to avoid holding
     * the transaction open during potentially slow export operations.
     *
     * @param user the user to delete
     * @return the result of the deletion operation
     * @throws IllegalArgumentException if user is null
     */
    public DeletionResult deleteUser(User user) {
        return deleteUser(user, gdprConfig.isExportBeforeDeletion());
    }

    /**
     * Deletes a user and all associated data, with explicit export control.
     *
     * <p>Note: Export is performed OUTSIDE the transaction to avoid holding
     * the transaction open during potentially slow export operations.
     *
     * @param user the user to delete
     * @param exportBeforeDeletion whether to export data before deletion
     * @return the result of the deletion operation
     * @throws IllegalArgumentException if user is null
     */
    public DeletionResult deleteUser(User user, boolean exportBeforeDeletion) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }

        Long userId = user.getId();

        log.info("GdprDeletionService.deleteUser: Starting GDPR deletion for user {}", userId);

        GdprExportDTO exportedData = null;

        try {
            // Step 1: Export data OUTSIDE transaction (avoids holding transaction during slow I/O)
            if (exportBeforeDeletion) {
                log.debug("GdprDeletionService.deleteUser: Exporting data before deletion for user {}", userId);
                exportedData = gdprExportService.exportUserData(user);
            }

            // Step 2: Perform deletion in transaction
            return executeUserDeletion(user, exportedData, exportBeforeDeletion);

        } catch (Exception e) {
            log.error("GdprDeletionService.deleteUser: Failed to delete user {}: {}",
                    userId, e.getMessage(), e);
            return DeletionResult.failure("Failed to delete user: " + e.getMessage());
        }
    }

    /**
     * Internal transactional method that performs the actual user deletion.
     *
     * @param user the user to delete
     * @param exportedData the pre-exported data (may be null)
     * @param wasExported whether export was performed
     * @return the result of the deletion operation
     */
    @Transactional
    protected DeletionResult executeUserDeletion(User user, GdprExportDTO exportedData, boolean wasExported) {
        Long userId = user.getId();
        String userEmail = user.getEmail();

        // Step 2: Notify all GdprDataContributors to prepare for deletion
        prepareContributorsForDeletion(user);

        // Step 3: Publish UserPreDeleteEvent for additional cleanup
        log.debug("GdprDeletionService.deleteUser: Publishing UserPreDeleteEvent for user {}", userId);
        eventPublisher.publishEvent(new UserPreDeleteEvent(this, user));

        // Step 4: Delete framework-managed data
        deleteFrameworkData(user);

        // Step 5: Delete the user entity
        log.debug("GdprDeletionService.deleteUser: Deleting user entity for {}", userId);
        userRepository.delete(user);

        log.info("GdprDeletionService.deleteUser: Successfully deleted user {}", userId);

        // Step 6: Publish UserDeletedEvent after successful deletion
        eventPublisher.publishEvent(new UserDeletedEvent(this, userId, userEmail, wasExported));

        return wasExported
                ? DeletionResult.successWithExport(exportedData)
                : DeletionResult.success(null);
    }

    /**
     * Notifies all GdprDataContributors to prepare for deletion.
     */
    private void prepareContributorsForDeletion(User user) {
        if (dataContributors == null || dataContributors.isEmpty()) {
            return;
        }

        for (GdprDataContributor contributor : dataContributors) {
            try {
                log.debug("GdprDeletionService.prepareContributorsForDeletion: Calling prepareForDeletion on '{}'",
                        contributor.getDataKey());
                contributor.prepareForDeletion(user);
            } catch (Exception e) {
                log.error("GdprDeletionService.prepareContributorsForDeletion: Contributor '{}' failed: {}",
                        contributor.getDataKey(), e.getMessage(), e);
                // Re-throw to trigger transaction rollback
                throw new RuntimeException("Data contributor '" + contributor.getDataKey() +
                        "' failed during deletion preparation: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Deletes framework-managed data associated with the user.
     */
    private void deleteFrameworkData(User user) {
        // Delete verification token
        VerificationToken verificationToken = verificationTokenRepository.findByUser(user);
        if (verificationToken != null) {
            log.debug("GdprDeletionService.deleteFrameworkData: Deleting verification token for user {}",
                    user.getId());
            verificationTokenRepository.delete(verificationToken);
        }

        // Delete password reset token
        PasswordResetToken passwordResetToken = passwordResetTokenRepository.findByUser(user);
        if (passwordResetToken != null) {
            log.debug("GdprDeletionService.deleteFrameworkData: Deleting password reset token for user {}",
                    user.getId());
            passwordResetTokenRepository.delete(passwordResetToken);
        }

        // Password history entries are deleted automatically via cascade (orphanRemoval = true)
    }

}
