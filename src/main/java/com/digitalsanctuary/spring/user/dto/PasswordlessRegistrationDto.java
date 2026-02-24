package com.digitalsanctuary.spring.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Data Transfer Object for passwordless user registration.
 * <p>
 * Used for registering users who will authenticate exclusively with passkeys,
 * without setting an initial password. Contains only the user's name and email.
 * </p>
 *
 * @author Devon Hillard
 */
@Data
public class PasswordlessRegistrationDto {

	/** The first name. */
	@NotBlank(message = "First name is required")
	@Size(max = 50, message = "First name must not exceed 50 characters")
	private String firstName;

	/** The last name. */
	@NotBlank(message = "Last name is required")
	@Size(max = 50, message = "Last name must not exceed 50 characters")
	private String lastName;

	/** The email. */
	@NotBlank(message = "Email is required")
	@Email(message = "Please provide a valid email address")
	@Size(max = 100, message = "Email must not exceed 100 characters")
	private String email;

	/** The role. */
	private Integer role;
}
