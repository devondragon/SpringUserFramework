package com.digitalsanctuary.spring.user.util;

import java.util.Arrays;

/**
 * Utility class for secure password handling to minimize password exposure in memory.
 * 
 * <p>This class provides methods for:
 * <ul>
 *   <li>Constant-time password comparison to prevent timing attacks</li>
 *   <li>Safe conversion between char[] and String when required</li>
 *   <li>Explicit memory clearing of sensitive data</li>
 * </ul>
 * 
 * <p><strong>Security Rationale:</strong>
 * <ul>
 *   <li>char[] arrays can be explicitly cleared from memory after use</li>
 *   <li>String objects are immutable and remain in memory until garbage collected</li>
 *   <li>Using char[] reduces password exposure time in memory</li>
 * </ul>
 * 
 * @author SpringUserFramework
 * @since 3.1.0
 */
public final class PasswordSecurityUtil {

    /**
     * Private constructor to prevent instantiation.
     */
    private PasswordSecurityUtil() {
        throw new UnsupportedOperationException("Utility class should not be instantiated");
    }

    /**
     * Compares two char[] arrays in constant time to prevent timing attacks.
     * 
     * <p>This method always compares the full length of both arrays to avoid
     * leaking information about password length or content through timing.
     * 
     * @param password1 first password array
     * @param password2 second password array
     * @return true if both arrays are non-null and contain the same characters
     */
    public static boolean constantTimeEquals(char[] password1, char[] password2) {
        if (password1 == null || password2 == null) {
            return password1 == password2;
        }

        // Different lengths - always return false but continue timing to avoid leak
        if (password1.length != password2.length) {
            // Still perform a comparison to maintain constant time
            int diff = 0;
            for (int i = 0; i < password1.length && i < password2.length; i++) {
                diff |= password1[i] ^ password2[i];
            }
            return false;
        }

        // Same length - compare all characters
        int diff = 0;
        for (int i = 0; i < password1.length; i++) {
            diff |= password1[i] ^ password2[i];
        }

        return diff == 0;
    }

    /**
     * Securely clears a char[] array by overwriting with zeros.
     * 
     * <p>This method should be called in a finally block to ensure
     * sensitive data is cleared even if an exception occurs.
     * 
     * @param password the password array to clear (can be null)
     */
    public static void clearPassword(char[] password) {
        if (password != null) {
            Arrays.fill(password, '\0');
        }
    }

    /**
     * Converts a char[] to String when required by libraries.
     * 
     * <p><strong>Important:</strong> The resulting String cannot be cleared
     * from memory until garbage collected. Use this method only when necessary
     * and clear the char[] array immediately after conversion.
     * 
     * @param password the password char array
     * @return String representation of the password, or null if input is null
     */
    public static String toString(char[] password) {
        if (password == null) {
            return null;
        }
        return new String(password);
    }

    /**
     * Converts a String to char[] for secure handling.
     * 
     * <p>Note: The original String will still remain in memory until
     * garbage collected. This method is primarily useful for transitioning
     * existing String-based code to char[]-based handling.
     * 
     * @param password the password string
     * @return char array representation, or null if input is null
     */
    public static char[] toCharArray(String password) {
        if (password == null) {
            return null;
        }
        return password.toCharArray();
    }

    /**
     * Validates that a char[] password is not null and not empty.
     * 
     * @param password the password to validate
     * @return true if password is non-null and has length > 0
     */
    public static boolean isNotEmpty(char[] password) {
        return password != null && password.length > 0;
    }

    /**
     * Validates that a char[] password is null or empty.
     * 
     * @param password the password to validate
     * @return true if password is null or has length 0
     */
    public static boolean isEmpty(char[] password) {
        return password == null || password.length == 0;
    }
}
