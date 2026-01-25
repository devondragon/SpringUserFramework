package com.digitalsanctuary.spring.user.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Data Transfer Object for saving a new password after password reset.
 * <p>
 * Used in the password reset flow after the user clicks the link in their email
 * and enters a new password. Contains the reset token and the new password with confirmation.
 * </p>
 *
 * @author Devon Hillard
 */
@Data
public class SavePasswordDto {

	/** The password reset token from the email link. */
	@NotNull
	@NotEmpty
	private String token;

	/** The new password to set. */
	@NotNull
	@NotEmpty
	private String newPassword;

	/** Confirmation of the new password (must match newPassword). */
	@NotNull
	@NotEmpty
	private String confirmPassword;
}
