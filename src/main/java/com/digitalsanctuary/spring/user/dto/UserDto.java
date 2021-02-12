package com.digitalsanctuary.spring.user.dto;

import lombok.Data;

/**
 * A user dto. This object is used for handling user related form data (registration, forms passing in email addresses,
 * etc...).
 */
@Data
public class UserDto {

	/** The first name. */
	private String firstName;

	/** The last name. */
	private String lastName;

	/** The password. */
	private String password;

	/** The matching password. */
	private String matchingPassword;

	/** The email. */
	private String email;

	/** The role. */
	private Integer role;
}
