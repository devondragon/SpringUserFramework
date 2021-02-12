package com.digitalsanctuary.spring.user.persistence.model;

import java.util.Collection;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;

import lombok.Data;

/**
 * The Role Entity. Part of the basic User ->> Role ->> Privilege structure.
 */
@Data
@Entity
public class Role {

	/** The id. */
	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private Long id;

	/** The users. */
	@ManyToMany(mappedBy = "roles")
	private Collection<User> users;

	/** The privileges. */
	@ManyToMany
	@JoinTable(name = "roles_privileges", joinColumns = @JoinColumn(name = "role_id", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "privilege_id", referencedColumnName = "id"))
	private Collection<Privilege> privileges;

	/** The name. */
	private String name;
}
