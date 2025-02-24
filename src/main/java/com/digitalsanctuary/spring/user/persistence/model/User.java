package com.digitalsanctuary.spring.user.persistence.model;

import java.util.Date;
import java.util.List;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import jakarta.persistence.*;
import lombok.Data;

/**
 * The User Entity. Part of the basic User ->> Role ->> Privilege structure. This is the primary user data object. You can add to this, or add
 * referenced types as needed. Leverages the Spring JPA Auditing framework to automatically manage the registrationDate and lastActivityDate fields.
 */
@Data
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "user_account")
public class User {

	/**
	 * Enum representing the available login providers.
	 */
	public enum Provider {
		/**
		 * Local authentication, typically using a username and password stored in the application's database.
		 */
		LOCAL,

		/**
		 * Login using Facebook as the authentication provider.
		 */
		FACEBOOK,

		/**
		 * Login using Google as the authentication provider.
		 */
		GOOGLE,

		/**
		 * Login using Apple as the authentication provider.
		 */
		APPLE,

		/**
		 * Login using Keycloak as the authentication provider.
		 */
		KEYCLOAK
	}

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
	@Column(unique = true, nullable = false)
	private String email;

	@Enumerated(EnumType.STRING)
	private Provider provider = Provider.LOCAL;

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

	private int failedLoginAttempts;

	/** The locked. */
	private boolean locked;

	@Temporal(TemporalType.TIMESTAMP)
	private Date lockedDate;

	/** The roles. */
	@ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE}, fetch = FetchType.EAGER)
	@JoinTable(name = "users_roles", joinColumns = @JoinColumn(name = "user_id", referencedColumnName = "id"),
			inverseJoinColumns = @JoinColumn(name = "role_id", referencedColumnName = "id"))
	private List<Role> roles;

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

	/**
	 * Gets the full name.
	 *
	 * @return the full name
	 */
	public String getFullName() {
		return firstName + " " + lastName;
	}
}
