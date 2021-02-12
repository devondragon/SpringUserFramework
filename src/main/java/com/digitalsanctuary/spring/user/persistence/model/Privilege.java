package com.digitalsanctuary.spring.user.persistence.model;

import java.util.Collection;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToMany;

import lombok.Data;

/**
 * The Privilege Entity. Part of the basic User ->> Role ->> Privilege structure.
 */
@Data
@Entity
public class Privilege {

	/** The id. */
	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private Long id;

	/** The name. */
	private String name;

	/** The roles. */
	@ManyToMany(mappedBy = "privileges")
	private Collection<Role> roles;
}
