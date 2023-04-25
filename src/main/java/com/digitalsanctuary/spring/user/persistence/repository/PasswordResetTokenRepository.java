package com.digitalsanctuary.spring.user.persistence.repository;

import java.util.Date;
import java.util.stream.Stream;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import com.digitalsanctuary.spring.user.persistence.model.PasswordResetToken;
import com.digitalsanctuary.spring.user.persistence.model.User;

/**
 * The Interface PasswordResetTokenRepository.
 */
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

	/**
	 * Find by token.
	 *
	 * @param token the token
	 * @return the password reset token
	 */
	PasswordResetToken findByToken(String token);

	/**
	 * Find by user.
	 *
	 * @param user the user
	 * @return the password reset token
	 */
	PasswordResetToken findByUser(User user);

	/**
	 * Find all by expiry date less than.
	 *
	 * @param now the now
	 * @return the stream
	 */
	Stream<PasswordResetToken> findAllByExpiryDateLessThan(Date now);

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
	@Query("delete from PasswordResetToken t where t.expiryDate <= ?1")
	void deleteAllExpiredSince(Date now);
}
