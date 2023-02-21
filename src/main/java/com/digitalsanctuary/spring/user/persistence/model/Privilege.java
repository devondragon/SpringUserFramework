package com.digitalsanctuary.spring.user.persistence.model;

import java.util.Collection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import lombok.Data;
import lombok.ToString;

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

	/** The description of the role. */
	private String description;

	/** The roles which have this privilege. */
	@ToString.Exclude
	@ManyToMany(mappedBy = "privileges")
	private Collection<Role> roles;

	public Privilege() {
		super();
	}

	public Privilege(final String name) {
		super();
		this.name = name;
	}

	public Privilege(final String name, final String description) {
		super();
		this.name = name;
		this.description = description;
	}
}
