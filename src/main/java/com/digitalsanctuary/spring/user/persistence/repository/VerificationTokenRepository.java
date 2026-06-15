package com.digitalsanctuary.spring.user.persistence.repository;

import java.util.Date;
import java.util.stream.Stream;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.model.VerificationToken;

/**
 * The Interface VerificationTokenRepository.
 */
public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {

	/**
	 * Find by token.
	 *
	 * @param token the token
	 * @return the verification token
	 */
	VerificationToken findByToken(String token);

	/**
	 * Find by user.
	 *
	 * @param user the user
	 * @return the verification token
	 */
	VerificationToken findByUser(User user);

	/**
	 * Delete all tokens for the given user. Used to enforce a single active token per user.
	 *
	 * @param user the user whose tokens should be deleted
	 */
	void deleteByUser(User user);

	/**
	 * Find all by expiry date less than.
	 *
	 * @param now the now
	 * @return the stream
	 */
	Stream<VerificationToken> findAllByExpiryDateLessThan(Date now);

	/**
	 * Delete by expiry date less than.
	 *
	 * @param now the now
	 */
	void deleteByExpiryDateLessThan(Date now);

	/**
	 * Delete all expired since.
	 *
	 * @param now the now
	 */
	@Modifying
	@Query("delete from VerificationToken t where t.expiryDate <= ?1")
	void deleteAllExpiredSince(Date now);

	/**
	 * Delete by token value using a direct DELETE query without fetching the entity first. Returns the
	 * number of rows removed, which makes the DELETE usable as an atomic single-use guard: under
	 * concurrent consumption the row lock serializes the deletes, so exactly one caller observes a
	 * count of {@code 1} and the rest observe {@code 0}.
	 *
	 * @param token the token value (stored hash) to delete
	 * @return the number of tokens deleted (0 or 1)
	 */
	@Modifying
	@Query("delete from VerificationToken t where t.token = ?1")
	int deleteByToken(String token);
}
