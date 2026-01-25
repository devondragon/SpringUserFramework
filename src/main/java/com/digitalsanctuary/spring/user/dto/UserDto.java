package com.digitalsanctuary.spring.user.dto;

import com.digitalsanctuary.spring.user.validation.PasswordMatches;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

/**
 * Data Transfer Object for user registration and related form data.
 * <p>
 * Used for handling user registration forms and related operations. Contains user details
 * including name, email, password with confirmation, and optional role assignment.
 * Validated using {@link PasswordMatches} to ensure password confirmation matches.
 * </p>
 *
 * @author Devon Hillard
 */
@Data
@PasswordMatches
public class UserDto {

	/** The first name. */
	@NotBlank(message = "First name is required")
	@Size(max = 50, message = "First name must not exceed 50 characters")
	private String firstName;

	/** The last name. */
	@NotBlank(message = "Last name is required")
	@Size(max = 50, message = "Last name must not exceed 50 characters")
	private String lastName;

	/** The password. */
	@ToString.Exclude
	@NotBlank(message = "Password is required")
	@Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
	private String password;

	/** The matching password. */
	@ToString.Exclude
	@NotBlank(message = "Password confirmation is required")
	private String matchingPassword;

	/** The email. */
	@NotBlank(message = "Email is required")
	@Email(message = "Please provide a valid email address")
	@Size(max = 100, message = "Email must not exceed 100 characters")
	private String email;

	/** The role. */
	private Integer role;
}
