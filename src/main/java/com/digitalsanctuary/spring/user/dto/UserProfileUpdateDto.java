package com.digitalsanctuary.spring.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Data Transfer Object for updating user profile information.
 * <p>
 * Contains only the editable profile fields (first name, last name).
 * Separate from {@link UserDto} to avoid requiring password fields during profile updates.
 * </p>
 *
 * @author Devon Hillard
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
