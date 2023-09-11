package com.digitalsanctuary.spring.user.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
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
	 * Delete.
	 *
	 * @param user the user
	 */
	@Override
	void delete(User user);
}
