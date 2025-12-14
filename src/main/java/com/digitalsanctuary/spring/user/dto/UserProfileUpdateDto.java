package com.digitalsanctuary.spring.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO for updating user profile information (first name, last name).
 * This is separate from UserDto to avoid requiring password fields during profile updates.
 */
@Data
public class UserProfileUpdateDto {

	/** The first name. */
	@NotBlank(message = "First name is required")
	@Size(max = 50, message = "First name must not exceed 50 characters")
	private String firstName;

	/** The last name. */
	@NotBlank(message = "Last name is required")
	@Size(max = 50, message = "Last name must not exceed 50 characters")
	private String lastName;
}
