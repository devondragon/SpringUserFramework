package com.digitalsanctuary.spring.user.persistence.repository;

import java.util.List;
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
