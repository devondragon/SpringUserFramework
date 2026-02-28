package com.digitalsanctuary.spring.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

/**
 * Data Transfer Object for setting an initial password on a passwordless account.
 * <p>
 * Used when a user who registered without a password (passkey-only) wants to add
 * a password to their account. Contains the new password and confirmation.
 * </p>
 *
 * @author Devon Hillard
 */
@Data
public class SetPasswordDto {

	/** The new password to set. */
	@ToString.Exclude
	@NotBlank(message = "Password is required")
	@Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
	private String newPassword;

	/** Confirmation of the new password (must match newPassword). */
	@ToString.Exclude
	@NotBlank(message = "Password confirmation is required")
	private String confirmPassword;
}
