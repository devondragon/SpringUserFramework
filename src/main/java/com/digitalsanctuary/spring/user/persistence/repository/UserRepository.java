package com.digitalsanctuary.spring.user.persistence.repository;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
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
	 * Find by email, eagerly loading the user's roles and each role's privileges in a single query via an entity graph.
	 *
	 * <p>This is the finder used on the authentication path (see {@code DSUserDetailsService}). Because {@code User.roles}
	 * and {@code Role.privileges} are now {@link jakarta.persistence.FetchType#LAZY}, callers that must traverse
	 * roles/privileges after the persistence session closes (e.g. building Spring Security authorities for a detached
	 * principal) must load the user through this method. The {@code @EntityGraph} ensures the full
	 * User &rarr; roles &rarr; privileges graph is initialized in one round trip, avoiding both the N+1 problem and a
	 * {@code LazyInitializationException}. The plain {@link #findByEmail(String)} remains for callers (token lookups,
	 * existence checks, lockout counters) that do not need the authority graph.</p>
	 *
	 * @param email the email
	 * @return the user with roles and privileges initialized, or {@code null} if none found
	 */
	@EntityGraph(attributePaths = {"roles", "roles.privileges"})
	User findWithRolesByEmail(String email);

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
	 * Find all enabled users.
	 *
	 * @return list of enabled users
	 */
	List<User> findAllByEnabledTrue();

	/**
	 * Delete.
	 *
	 * @param user the user
	 */
	@Override
	void delete(User user);
}
