package com.digitalsanctuary.spring.user.persistence.model;

import java.util.Collection;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityListeners;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import lombok.Data;

/**
 * The User Entity. Part of the basic User ->> Role ->> Privilege structure. This is the primary user data object. You
 * can add to this, or add referenced types as needed. Leverages the Spring JPA Auditing framework to automatically
 * manage the registrationDate and lastActivityDate fields.
 */
@Data
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "user_account")
public class User {

	/** The id. */
	@Id
	@Column(unique = true, nullable = false)
	@GeneratedValue(strategy = GenerationType.AUTO)
	private Long id;

	/** The first name. */
	private String firstName;

	/** The last name. */
	private String lastName;

	/** The email. */
	private String email;

	/** The password. */
	@Column(length = 60)
	private String password;

	/** The enabled. */
	private boolean enabled;

	/** The registration date. */
	@CreatedDate
	@Temporal(TemporalType.TIMESTAMP)
	private Date registrationDate;

	/** The last activity date. */
	@LastModifiedDate
	@Temporal(TemporalType.TIMESTAMP)
	private Date lastActivityDate;

	/** The locked. */
	private boolean locked;

	/** The roles. */
	@ManyToMany(fetch = FetchType.EAGER)
	@JoinTable(name = "users_roles", joinColumns = @JoinColumn(name = "user_id", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "role_id", referencedColumnName = "id"))
	private Collection<Role> roles;

	/**
	 * Instantiates a new user.
	 */
	public User() {
		super();
		this.enabled = false;
	}

	/**
	 * Sets the last activity date.
	 */
	@PreUpdate
	public void setLastActivityDate() {
		setLastActivityDate(new Date());
	}
}
