package com.digitalsanctuary.spring.user.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.digitalsanctuary.spring.user.persistence.model.Role;

/**
 * The Interface RoleRepository.
 */
public interface RoleRepository extends JpaRepository<Role, Long> {

	/**
	 * Find by name.
	 *
	 * @param name the name
	 * @return the role
	 */
	Role findByName(String name);

	/**
	 * Delete.
	 *
	 * @param role the role
	 */
	@Override
	void delete(Role role);
}
