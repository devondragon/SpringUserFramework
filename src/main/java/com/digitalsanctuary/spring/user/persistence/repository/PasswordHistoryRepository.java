package com.digitalsanctuary.spring.user.persistence.repository;

import com.digitalsanctuary.spring.user.persistence.model.PasswordHistoryEntry;
import com.digitalsanctuary.spring.user.persistence.model.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * Fetch the ids of a user's password history entries, newest first.
     *
     * <p>Used to locate the cutoff id when pruning old entries. Ordering is by primary
     * key descending: the id column is generated with {@code GenerationType.IDENTITY},
     * so it is monotonically increasing and therefore a reliable recency ordering even
     * when multiple entries share the same {@code entryDate} timestamp.
     *
     * @param user     the user
     * @param pageable the pageable defining the offset/limit (used to fetch only the cutoff row)
     * @return list of entry ids, newest first
     */
    @Query("SELECT p.id FROM PasswordHistoryEntry p WHERE p.user = :user ORDER BY p.id DESC")
    List<Long> findIdsByUserOrderByIdDesc(@Param("user") User user, Pageable pageable);

    /**
     * Delete a user's password history entries whose id is below the given cutoff.
     *
     * <p>This is a single set-based delete that prunes everything older than the cutoff
     * id in one statement, avoiding the load-then-deleteAll read/delete window. It is
     * portable across H2, MariaDB, and PostgreSQL (no subquery {@code LIMIT}).
     *
     * @param user     the user whose old entries should be removed
     * @param cutoffId entries with an id strictly less than this are deleted
     * @return the number of rows deleted
     */
    @Modifying
    @Query("DELETE FROM PasswordHistoryEntry p WHERE p.user = :user AND p.id < :cutoffId")
    int deleteByUserAndIdLessThan(@Param("user") User user, @Param("cutoffId") Long cutoffId);

    /**
     * Delete all password history entries for a user.
     * Used when removing a user's password for passwordless accounts.
     *
     * @param user the user whose history should be deleted
     */
    void deleteByUser(User user);
}
