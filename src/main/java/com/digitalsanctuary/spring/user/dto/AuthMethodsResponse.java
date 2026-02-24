package com.digitalsanctuary.spring.user.dto;

import com.digitalsanctuary.spring.user.persistence.model.User;
import lombok.Builder;
import lombok.Data;

/**
 * Response DTO for the auth-methods endpoint.
 * <p>
 * Provides information about which authentication methods are configured
 * for the current user, enabling the UI to show/hide relevant options.
 * </p>
 *
 * @author Devon Hillard
 */
@Data
@Builder
public class AuthMethodsResponse {

	/** Whether the user has a password set. */
	private boolean hasPassword;

	/** Whether the user has any passkeys registered. */
	private boolean hasPasskeys;

	/** The number of passkeys registered. */
	private long passkeysCount;

	/** Whether WebAuthn is enabled on the server. */
	private boolean webAuthnEnabled;

	/** The user's authentication provider. */
	private User.Provider provider;
}
