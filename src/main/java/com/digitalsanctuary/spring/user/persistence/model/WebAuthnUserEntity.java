package com.digitalsanctuary.spring.user.persistence.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * JPA entity for the {@code user_entities} table. Maps WebAuthn user handles to application users. The {@code id} is
 * the Base64url-encoded WebAuthn user handle.
 */
@Data
@Entity
@Table(name = "user_entities")
public class WebAuthnUserEntity {

	/** Base64url-encoded WebAuthn user handle. */
	@Id
	private String id;

	/** Username (email). */
	@Column(unique = true, nullable = false)
	private String name;

	/** User full name for display. */
	@Column(name = "display_name", nullable = false)
	private String displayName;

	/** FK to the application user. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_account_id")
	private User user;
}
