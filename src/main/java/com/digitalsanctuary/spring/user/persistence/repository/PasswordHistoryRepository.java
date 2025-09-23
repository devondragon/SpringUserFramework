package com.digitalsanctuary.spring.user.persistence.repository;

import com.digitalsanctuary.spring.user.persistence.model.PasswordHistoryEntry;
import com.digitalsanctuary.spring.user.persistence.model.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * The Interface PasswordHistoryRepository.
 * Handles CRUD operations for Password History entries.
 */
public interface PasswordHistoryRepository extends JpaRepository<PasswordHistoryEntry, Long> {

    /**
     * Fetch the most recent password hashes for a user, limited by pageable.
     * Used for checking against password reuse.
     *
     * @param user     the user
     * @param pageable the pageable object defining limit
     * @return list of password hashes
     */
    @Query("SELECT p.passwordHash FROM PasswordHistoryEntry p WHERE p.user = :user ORDER BY p.entryDate DESC")
    List<String> findRecentPasswordHashes(User user, Pageable pageable);

    /**
     * Get all history entries for a user ordered by newest first.
     * Used for pruning old entries.
     *
     * @param user the user
     * @return list of password history entries
     */
    List<PasswordHistoryEntry> findByUserOrderByEntryDateDesc(User user);
}
