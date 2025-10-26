package com.digitalsanctuary.spring.user.dto;

import com.digitalsanctuary.spring.user.util.PasswordSecurityUtil;
import com.digitalsanctuary.spring.user.validation.PasswordMatches;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

/**
 * A user dto. This object is used for handling user related form data (registration, forms passing in email addresses,
 * etc...).
 * 
 * <p>Note: This DTO supports both String and char[] for passwords. The char[] methods are preferred
 * for enhanced security as they allow explicit memory clearing. String methods are maintained
 * for backward compatibility.
 */
@Data
@PasswordMatches
public class UserDto implements AutoCloseable {

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

	/** The password as char array (for secure handling). */
	@ToString.Exclude
	private transient char[] passwordChars;

	/** The matching password as char array (for secure handling). */
	@ToString.Exclude
	private transient char[] matchingPasswordChars;

	/**
	 * Gets the password as a char array for secure handling.
	 * If passwordChars is null but password is set, converts from String.
	 * 
	 * @return the password as char array, or null if not set
	 */
	public char[] getPasswordChars() {
		if (passwordChars == null && password != null) {
			return PasswordSecurityUtil.toCharArray(password);
		}
		return passwordChars;
	}

	/**
	 * Sets the password from a char array (preferred for security).
	 * Also updates the String password field for backward compatibility.
	 * 
	 * @param passwordChars the password as char array
	 */
	public void setPasswordChars(char[] passwordChars) {
		this.passwordChars = passwordChars;
		if (passwordChars != null) {
			this.password = PasswordSecurityUtil.toString(passwordChars);
		} else {
			this.password = null;
		}
	}

	/**
	 * Gets the matching password as a char array for secure handling.
	 * If matchingPasswordChars is null but matchingPassword is set, converts from String.
	 * 
	 * @return the matching password as char array, or null if not set
	 */
	public char[] getMatchingPasswordChars() {
		if (matchingPasswordChars == null && matchingPassword != null) {
			return PasswordSecurityUtil.toCharArray(matchingPassword);
		}
		return matchingPasswordChars;
	}

	/**
	 * Sets the matching password from a char array (preferred for security).
	 * Also updates the String matchingPassword field for backward compatibility.
	 * 
	 * @param matchingPasswordChars the matching password as char array
	 */
	public void setMatchingPasswordChars(char[] matchingPasswordChars) {
		this.matchingPasswordChars = matchingPasswordChars;
		if (matchingPasswordChars != null) {
			this.matchingPassword = PasswordSecurityUtil.toString(matchingPasswordChars);
		} else {
			this.matchingPassword = null;
		}
	}

	/**
	 * Clears sensitive password data from memory.
	 * This method should be called in a finally block to ensure passwords are cleared.
	 */
	public void clearPasswords() {
		PasswordSecurityUtil.clearPassword(passwordChars);
		PasswordSecurityUtil.clearPassword(matchingPasswordChars);
		passwordChars = null;
		matchingPasswordChars = null;
	}

	/**
	 * Closes this resource, clearing password data from memory.
	 */
	@Override
	public void close() {
		clearPasswords();
	}
}
