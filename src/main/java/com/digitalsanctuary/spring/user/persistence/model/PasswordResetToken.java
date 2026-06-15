package com.digitalsanctuary.spring.user.persistence.model;

import java.util.Calendar;
import java.util.Date;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * The PasswordResetToken Entity.
 */
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
@Entity
public class PasswordResetToken {

	/** The Constant EXPIRATION. Default token lifetime in minutes (24h) used by the legacy constructors. */
	private static final int EXPIRATION = 60 * 24;

	/** The id. */
	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	// Identity-based equality keys on this id only. Two transient (unsaved, id == null) instances compare equal,
	// so never use unsaved entities as Set/Map keys; add them only after they have been persisted. See EntityEqualityTest.
	@EqualsAndHashCode.Include
	private Long id;

	/** The token. */
	@ToString.Exclude
	@Column(name = "token", nullable = false, unique = true)
	private String token;

	/** The user. */
	// EAGER so getUser() works on a detached token; note the loaded User's roles are still LAZY — use UserRepository.findWithRolesByEmail if you need authorities.
	@ToString.Exclude
	@OneToOne(targetEntity = User.class, fetch = FetchType.EAGER)
	@JoinColumn(nullable = false, name = "user_id")
	private User user;

	/** The expiry date. */
	private Date expiryDate;

	/**
	 * Instantiates a new password reset token.
	 */
	public PasswordResetToken() {
		super();
	}

	/**
	 * Instantiates a new password reset token.
	 *
	 * @param token the token
	 */
	public PasswordResetToken(final String token) {
		super();
		this.token = token;
		this.expiryDate = calculateExpiryDate(EXPIRATION);
	}

	/**
	 * Instantiates a new password reset token.
	 *
	 * @param token the token
	 * @param user the user
	 */
	public PasswordResetToken(final String token, final User user) {
		super();
		this.token = token;
		this.user = user;
		this.expiryDate = calculateExpiryDate(EXPIRATION);
	}

	/**
	 * Instantiates a new password reset token with a configurable lifetime.
	 *
	 * @param token the token (already hashed for storage by the calling service)
	 * @param user the user
	 * @param expiryTimeInMinutes the token lifetime in minutes
	 */
	public PasswordResetToken(final String token, final User user, final int expiryTimeInMinutes) {
		super();
		this.token = token;
		this.user = user;
		this.expiryDate = calculateExpiryDate(expiryTimeInMinutes);
	}

	/**
	 * Calculate expiry date.
	 *
	 * @param expiryTimeInMinutes the expiry time in minutes
	 * @return the date
	 */
	private Date calculateExpiryDate(final int expiryTimeInMinutes) {
		final Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(new Date().getTime());
		cal.add(Calendar.MINUTE, expiryTimeInMinutes);
		return new Date(cal.getTime().getTime());
	}

	/**
	 * Update token.
	 *
	 * @param token the token
	 */
	public void updateToken(final String token) {
		this.token = token;
		this.expiryDate = calculateExpiryDate(EXPIRATION);
	}

}
