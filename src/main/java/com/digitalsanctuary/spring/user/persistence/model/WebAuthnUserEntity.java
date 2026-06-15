package com.digitalsanctuary.spring.user.persistence.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * JPA entity for the {@code user_entities} table. Maps WebAuthn user handles to application users. The {@code id} is
 * the Base64url-encoded WebAuthn user handle.
 */
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
@Entity
@Table(name = "user_entities")
public class WebAuthnUserEntity {

	/** Base64url-encoded WebAuthn user handle. */
	@Id
	// Identity-based equality keys on this id only. Two instances with a null id compare equal,
	// so never use unsaved entities as Set/Map keys; add them only after the id is assigned. See EntityEqualityTest.
	@EqualsAndHashCode.Include
	private String id;

	/** Username (email). */
	@Column(unique = true, nullable = false)
	private String name;

	/** User full name for display. */
	@Column(name = "display_name", nullable = false)
	private String displayName;

	/** FK to the application user. */
	@ToString.Exclude
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_account_id", nullable = false)
	private User user;
}
