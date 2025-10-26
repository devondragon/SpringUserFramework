package com.digitalsanctuary.spring.user.dto;

import com.digitalsanctuary.spring.user.util.PasswordSecurityUtil;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

/**
 * A new password dto. This object is used for password form actions (change password, forgot password token, save
 * password, etc..).
 * 
 * <p>Note: This DTO supports both String and char[] for passwords. The char[] methods are preferred
 * for enhanced security as they allow explicit memory clearing. String methods are maintained
 * for backward compatibility.
 */
@Data
public class PasswordDto implements AutoCloseable {

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

	/** The old password as char array (for secure handling). */
	@ToString.Exclude
	private transient char[] oldPasswordChars;

	/** The new password as char array (for secure handling). */
	@ToString.Exclude
	private transient char[] newPasswordChars;

	/**
	 * Gets the old password as a char array for secure handling.
	 * If oldPasswordChars is null but oldPassword is set, converts from String.
	 * 
	 * @return the old password as char array, or null if not set
	 */
	public char[] getOldPasswordChars() {
		if (oldPasswordChars == null && oldPassword != null) {
			return PasswordSecurityUtil.toCharArray(oldPassword);
		}
		return oldPasswordChars;
	}

	/**
	 * Sets the old password from a char array (preferred for security).
	 * Also updates the String oldPassword field for backward compatibility.
	 * 
	 * @param oldPasswordChars the old password as char array
	 */
	public void setOldPasswordChars(char[] oldPasswordChars) {
		this.oldPasswordChars = oldPasswordChars;
		if (oldPasswordChars != null) {
			this.oldPassword = PasswordSecurityUtil.toString(oldPasswordChars);
		} else {
			this.oldPassword = null;
		}
	}

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
	 * Clears sensitive password data from memory.
	 * This method should be called in a finally block to ensure passwords are cleared.
	 */
	public void clearPasswords() {
		PasswordSecurityUtil.clearPassword(oldPasswordChars);
		PasswordSecurityUtil.clearPassword(newPasswordChars);
		oldPasswordChars = null;
		newPasswordChars = null;
	}

	/**
	 * Closes this resource, clearing password data from memory.
	 */
	@Override
	public void close() {
		clearPasswords();
	}
}
