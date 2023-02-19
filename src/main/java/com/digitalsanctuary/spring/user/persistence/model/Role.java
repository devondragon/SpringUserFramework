package com.digitalsanctuary.spring.user.persistence.model;

import java.util.Collection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import lombok.Data;
import lombok.ToString;

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
	@ToString.Exclude
	@ManyToMany(mappedBy = "roles")
	private Collection<User> users;

	/** The privileges. */
	@ManyToMany
	@JoinTable(name = "roles_privileges", joinColumns = @JoinColumn(name = "role_id", referencedColumnName = "id"),
			inverseJoinColumns = @JoinColumn(name = "privilege_id", referencedColumnName = "id"))
	private Collection<Privilege> privileges;

	/** The name. */
	private String name;

	private String description;


	public Role() {
		super();
	}

	public Role(final String name) {
		super();
		this.name = name;
	}

	public Role(final String name, final String description) {
		super();
		this.name = name;
		this.description = description;
	}
}
