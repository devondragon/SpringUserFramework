package com.digitalsanctuary.spring.user.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for PasswordSecurityUtil.
 */
class PasswordSecurityUtilTest {

    @Test
    void constantTimeEquals_returnsTrue_whenBothNull() {
        assertTrue(PasswordSecurityUtil.constantTimeEquals(null, null));
    }

    @Test
    void constantTimeEquals_returnsFalse_whenOneNull() {
        assertFalse(PasswordSecurityUtil.constantTimeEquals(null, "password".toCharArray()));
        assertFalse(PasswordSecurityUtil.constantTimeEquals("password".toCharArray(), null));
    }

    @Test
    void constantTimeEquals_returnsTrue_whenIdentical() {
        char[] password1 = "MySecureP@ssw0rd".toCharArray();
        char[] password2 = "MySecureP@ssw0rd".toCharArray();
        assertTrue(PasswordSecurityUtil.constantTimeEquals(password1, password2));
    }

    @Test
    void constantTimeEquals_returnsFalse_whenDifferent() {
        char[] password1 = "MySecureP@ssw0rd".toCharArray();
        char[] password2 = "DifferentP@ss".toCharArray();
        assertFalse(PasswordSecurityUtil.constantTimeEquals(password1, password2));
    }

    @Test
    void constantTimeEquals_returnsFalse_whenDifferentLength() {
        char[] password1 = "short".toCharArray();
        char[] password2 = "muchlongerpassword".toCharArray();
        assertFalse(PasswordSecurityUtil.constantTimeEquals(password1, password2));
    }

    @Test
    void constantTimeEquals_returnsFalse_whenOneCharacterDifferent() {
        char[] password1 = "MySecureP@ssw0rd".toCharArray();
        char[] password2 = "MySecureP@ssw0rD".toCharArray(); // last char different
        assertFalse(PasswordSecurityUtil.constantTimeEquals(password1, password2));
    }

    @Test
    void constantTimeEquals_returnsTrue_whenBothEmpty() {
        char[] password1 = new char[0];
        char[] password2 = new char[0];
        assertTrue(PasswordSecurityUtil.constantTimeEquals(password1, password2));
    }

    @Test
    void clearPassword_clearsArray() {
        char[] password = "MySecureP@ssw0rd".toCharArray();
        PasswordSecurityUtil.clearPassword(password);
        
        // Verify all characters are cleared to '\0'
        for (char c : password) {
            assertEquals('\0', c, "Password should be cleared to null characters");
        }
    }

    @Test
    void clearPassword_handlesNull() {
        // Should not throw exception
        assertDoesNotThrow(() -> PasswordSecurityUtil.clearPassword(null));
    }

    @Test
    void clearPassword_handlesEmptyArray() {
        char[] password = new char[0];
        assertDoesNotThrow(() -> PasswordSecurityUtil.clearPassword(password));
    }

    @Test
    void toString_convertsCorrectly() {
        char[] password = "MySecureP@ssw0rd".toCharArray();
        String result = PasswordSecurityUtil.toString(password);
        assertEquals("MySecureP@ssw0rd", result);
    }

    @Test
    void toString_handlesNull() {
        assertNull(PasswordSecurityUtil.toString(null));
    }

    @Test
    void toString_handlesEmptyArray() {
        char[] password = new char[0];
        String result = PasswordSecurityUtil.toString(password);
        assertEquals("", result);
    }

    @Test
    void toCharArray_convertsCorrectly() {
        String password = "MySecureP@ssw0rd";
        char[] result = PasswordSecurityUtil.toCharArray(password);
        assertArrayEquals("MySecureP@ssw0rd".toCharArray(), result);
    }

    @Test
    void toCharArray_handlesNull() {
        assertNull(PasswordSecurityUtil.toCharArray(null));
    }

    @Test
    void toCharArray_handlesEmptyString() {
        String password = "";
        char[] result = PasswordSecurityUtil.toCharArray(password);
        assertNotNull(result);
        assertEquals(0, result.length);
    }

    @Test
    void isNotEmpty_returnsTrue_whenNotEmpty() {
        char[] password = "password".toCharArray();
        assertTrue(PasswordSecurityUtil.isNotEmpty(password));
    }

    @Test
    void isNotEmpty_returnsFalse_whenNull() {
        assertFalse(PasswordSecurityUtil.isNotEmpty(null));
    }

    @Test
    void isNotEmpty_returnsFalse_whenEmpty() {
        char[] password = new char[0];
        assertFalse(PasswordSecurityUtil.isNotEmpty(password));
    }

    @Test
    void isEmpty_returnsTrue_whenNull() {
        assertTrue(PasswordSecurityUtil.isEmpty(null));
    }

    @Test
    void isEmpty_returnsTrue_whenEmpty() {
        char[] password = new char[0];
        assertTrue(PasswordSecurityUtil.isEmpty(password));
    }

    @Test
    void isEmpty_returnsFalse_whenNotEmpty() {
        char[] password = "password".toCharArray();
        assertFalse(PasswordSecurityUtil.isEmpty(password));
    }

    @Test
    void constructor_throwsException() {
        assertThrows(java.lang.reflect.InvocationTargetException.class, () -> {
            // Use reflection to try to instantiate
            java.lang.reflect.Constructor<PasswordSecurityUtil> constructor = 
                PasswordSecurityUtil.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            constructor.newInstance();
        });
    }

    @Test
    void integrationTest_securePasswordFlow() {
        // Simulate a secure password handling flow
        String userInput = "MySecureP@ssw0rd!";
        
        // 1. Convert to char[]
        char[] password = PasswordSecurityUtil.toCharArray(userInput);
        assertNotNull(password);
        
        // 2. Validate
        assertTrue(PasswordSecurityUtil.isNotEmpty(password));
        
        // 3. Compare with another password
        char[] matchingPassword = "MySecureP@ssw0rd!".toCharArray();
        assertTrue(PasswordSecurityUtil.constantTimeEquals(password, matchingPassword));
        
        // 4. Convert to String when needed (e.g., for PasswordEncoder)
        String passwordString = PasswordSecurityUtil.toString(password);
        assertEquals(userInput, passwordString);
        
        // 5. Clear both arrays
        PasswordSecurityUtil.clearPassword(password);
        PasswordSecurityUtil.clearPassword(matchingPassword);
        
        // 6. Verify cleared
        for (char c : password) {
            assertEquals('\0', c);
        }
        for (char c : matchingPassword) {
            assertEquals('\0', c);
        }
    }
}
