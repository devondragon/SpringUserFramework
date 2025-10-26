package com.digitalsanctuary.spring.user.dto;

import com.digitalsanctuary.spring.user.util.PasswordSecurityUtil;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.ToString;

/**
 * Data Transfer Object for saving a new password after password reset.
 * Used in the password reset flow after the user clicks the link in their email
 * and enters a new password.
 * 
 * <p>Note: This DTO supports both String and char[] for passwords. The char[] methods are preferred
 * for enhanced security as they allow explicit memory clearing. String methods are maintained
 * for backward compatibility.
 */
@Data
public class SavePasswordDto implements AutoCloseable {

	/** The password reset token from the email link. */
	@NotNull
	@NotEmpty
	private String token;

	/** The new password to set. */
	@ToString.Exclude
	@NotNull
	@NotEmpty
	private String newPassword;

	/** Confirmation of the new password (must match newPassword). */
	@ToString.Exclude
	@NotNull
	@NotEmpty
	private String confirmPassword;

	/** The new password as char array (for secure handling). */
	@ToString.Exclude
	private transient char[] newPasswordChars;

	/** The confirm password as char array (for secure handling). */
	@ToString.Exclude
	private transient char[] confirmPasswordChars;

	/**
	 * Gets the new password as a char array for secure handling.
	 * If newPasswordChars is null but newPassword is set, converts from String.
	 * 
	 * @return the new password as char array, or null if not set
	 */
	public char[] getNewPasswordChars() {
		if (newPasswordChars == null && newPassword != null) {
			return PasswordSecurityUtil.toCharArray(newPassword);
		}
		return newPasswordChars;
	}

	/**
	 * Sets the new password from a char array (preferred for security).
	 * Also updates the String newPassword field for backward compatibility.
	 * 
	 * @param newPasswordChars the new password as char array
	 */
	public void setNewPasswordChars(char[] newPasswordChars) {
		this.newPasswordChars = newPasswordChars;
		if (newPasswordChars != null) {
			this.newPassword = PasswordSecurityUtil.toString(newPasswordChars);
		} else {
			this.newPassword = null;
		}
	}

	/**
	 * Gets the confirm password as a char array for secure handling.
	 * If confirmPasswordChars is null but confirmPassword is set, converts from String.
	 * 
	 * @return the confirm password as char array, or null if not set
	 */
	public char[] getConfirmPasswordChars() {
		if (confirmPasswordChars == null && confirmPassword != null) {
			return PasswordSecurityUtil.toCharArray(confirmPassword);
		}
		return confirmPasswordChars;
	}

	/**
	 * Sets the confirm password from a char array (preferred for security).
	 * Also updates the String confirmPassword field for backward compatibility.
	 * 
	 * @param confirmPasswordChars the confirm password as char array
	 */
	public void setConfirmPasswordChars(char[] confirmPasswordChars) {
		this.confirmPasswordChars = confirmPasswordChars;
		if (confirmPasswordChars != null) {
			this.confirmPassword = PasswordSecurityUtil.toString(confirmPasswordChars);
		} else {
			this.confirmPassword = null;
		}
	}

	/**
	 * Clears sensitive password data from memory.
	 * This method should be called in a finally block to ensure passwords are cleared.
	 */
	public void clearPasswords() {
		PasswordSecurityUtil.clearPassword(newPasswordChars);
		PasswordSecurityUtil.clearPassword(confirmPasswordChars);
		newPasswordChars = null;
		confirmPasswordChars = null;
	}

	/**
	 * Closes this resource, clearing password data from memory.
	 */
	@Override
	public void close() {
		clearPasswords();
	}
}
