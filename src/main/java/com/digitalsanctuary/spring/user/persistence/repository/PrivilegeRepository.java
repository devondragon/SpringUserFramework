package com.digitalsanctuary.spring.user.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.digitalsanctuary.spring.user.persistence.model.Privilege;

/**
 * The Interface PrivilegeRepository.
 */
public interface PrivilegeRepository extends JpaRepository<Privilege, Long> {

	/**
	 * Find by name.
	 *
	 * @param name the name
	 * @return the privilege
	 */
	Privilege findByName(String name);

	/**
	 * Delete.
	 *
	 * @param privilege the privilege
	 */
	@Override
	void delete(Privilege privilege);
}
