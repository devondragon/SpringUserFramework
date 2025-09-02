package com.digitalsanctuary.spring.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * A new password dto. This object is used for password form actions (change password, forgot password token, save
 * password, etc..).
 */
@Data
public class PasswordDto {

	/** The old password. */
	@NotBlank(message = "Current password is required")
	private String oldPassword;

	/** The new password. */
	@NotBlank(message = "New password is required")
	@Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
	private String newPassword;

	/** The token. */
	private String token;

}
