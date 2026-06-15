package com.digitalsanctuary.spring.user.persistence.model;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * The Role Entity. Part of the basic User ->> Role ->> Privilege structure.
 */
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
@Entity
public class Role implements Serializable {

	/** The Constant serialVersionUID. */
	private static final long serialVersionUID = 1L;

	/** The id. */
	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	@EqualsAndHashCode.Include
	@ToString.Include
	private Long id;

	/** The users. */
	@ToString.Exclude
	@ManyToMany(mappedBy = "roles", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
	private Set<User> users = new HashSet<>();

	/** The privileges. */
	// EAGER by design: privileges are small, static reference data and there is no hot path that loads many Roles at
	// once, so eager-loading them is cheap. Keeping this EAGER lets consumers call role.getPrivileges() outside a
	// transaction without a LazyInitializationException. The user-load N+1 is addressed by User.roles being LAZY (see
	// User), and the authentication path still fetches the full graph in one query via
	// UserRepository.findWithRolesByEmail. (Role.privileges was briefly LAZY in 5.0.0; reverted in 5.0.1.)
	@ToString.Exclude
	@ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE}, fetch = FetchType.EAGER)
	@JoinTable(name = "roles_privileges", joinColumns = @JoinColumn(name = "role_id", referencedColumnName = "id"),
			inverseJoinColumns = @JoinColumn(name = "privilege_id", referencedColumnName = "id"))
	private Set<Privilege> privileges = new HashSet<>();

	/** The name. */
	@Column(unique = true, nullable = false)
	private String name;

	private String description;

	/**
	 * Instantiates a new role.
	 */
	public Role() {
		super();
	}

	/**
	 * Instantiates a new role.
	 *
	 * @param name the name
	 */
	public Role(final String name) {
		super();
		this.name = name;
	}

	/**
	 * Instantiates a new role.
	 *
	 * @param name the name
	 * @param description the description
	 */
	public Role(final String name, final String description) {
		super();
		this.name = name;
		this.description = description;
	}
}
