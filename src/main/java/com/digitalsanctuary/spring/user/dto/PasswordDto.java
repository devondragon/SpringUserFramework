package com.digitalsanctuary.spring.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

/**
 * Data Transfer Object for password change operations.
 * <p>
 * Used for password form actions including change password and forgot password token flows.
 * Contains the old password, new password, and optional reset token.
 * </p>
 *
 * @author Digital Sanctuary
 */
@Data
public class PasswordDto {

	/** The old password. */
	@ToString.Exclude
	@NotBlank(message = "Current password is required")
	private String oldPassword;

	/** The new password. */
	@ToString.Exclude
	@NotBlank(message = "New password is required")
	@Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
	private String newPassword;

	/** The token. */
	private String token;

}
