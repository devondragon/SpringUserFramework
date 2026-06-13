package com.digitalsanctuary.spring.user.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.digitalsanctuary.spring.user.persistence.model.User;

/**
 * The Interface UserRepository.
 */
public interface UserRepository extends JpaRepository<User, Long> {

	/**
	 * Find by email.
	 *
	 * @param email the email
	 * @return the user
	 */
	User findByEmail(String email);

	/**
	 * Atomically increments the failed login attempt counter for the user with the given email.
	 *
	 * <p>This is a single bulk UPDATE statement executed directly against the database, which avoids the lost-update race that a read-modify-write
	 * (read counter, increment in memory, save) would suffer under concurrent failed logins. Each concurrent increment is serialized by the database
	 * so no increment is lost, ensuring an attacker cannot evade lockout by hammering an account in parallel.</p>
	 *
	 * <p>{@code flushAutomatically = true} flushes any pending persistence-context changes before the bulk update so they are not lost.
	 * {@code clearAutomatically = true} clears the persistence context afterward; the bulk UPDATE bypasses the first-level cache, so clearing ensures a
	 * subsequent {@code findByEmail} reads the fresh, incremented value from the database rather than a stale cached entity.</p>
	 *
	 * @param email the email of the user whose counter should be incremented
	 * @return the number of rows affected (1 if the user exists, 0 otherwise)
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update User u set u.failedLoginAttempts = u.failedLoginAttempts + 1 where u.email = :email")
	int incrementFailedAttempts(@Param("email") String email);

	/**
	 * Delete.
	 *
	 * @param user the user
	 */
	@Override
	void delete(User user);
}
