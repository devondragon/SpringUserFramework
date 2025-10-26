package com.digitalsanctuary.spring.user.dto;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.digitalsanctuary.spring.user.util.PasswordSecurityUtil;

/**
 * Unit tests for DTO password char[] handling.
 */
class PasswordDtoCharArrayTest {

    @Test
    void userDto_getPasswordChars_returnsCharArray() {
        UserDto dto = new UserDto();
        dto.setPassword("TestP@ssw0rd");
        
        char[] passwordChars = dto.getPasswordChars();
        char[] expected = "TestP@ssw0rd".toCharArray();
        try {
            assertNotNull(passwordChars);
            assertArrayEquals(expected, passwordChars);
        } finally {
            PasswordSecurityUtil.clearPassword(expected);
        }
    }

    @Test
    void userDto_setPasswordChars_updatesStringPassword() {
        UserDto dto = new UserDto();
        char[] password = "TestP@ssw0rd".toCharArray();
        dto.setPasswordChars(password);
        
        assertEquals("TestP@ssw0rd", dto.getPassword());
    }

    @Test
    void userDto_clearPasswords_clearsArrays() {
        UserDto dto = new UserDto();
        char[] password = "TestP@ssw0rd".toCharArray();
        char[] matching = "TestP@ssw0rd".toCharArray();
        
        dto.setPasswordChars(password);
        dto.setMatchingPasswordChars(matching);
        
        dto.clearPasswords();
        
        // Verify arrays are cleared
        for (char c : password) {
            assertEquals('\0', c);
        }
        for (char c : matching) {
            assertEquals('\0', c);
        }
    }

    @Test
    void userDto_autoCloseable_clearsPasswordsOnClose() {
        UserDto dto = new UserDto();
        char[] password = "TestP@ssw0rd".toCharArray();
        dto.setPasswordChars(password);
        
        // Try-with-resources should call close()
        try (UserDto closeable = dto) {
            assertNotNull(closeable.getPassword());
        }
        
        // Verify password is cleared
        for (char c : password) {
            assertEquals('\0', c);
        }
    }

    @Test
    void passwordDto_getOldPasswordChars_returnsCharArray() {
        PasswordDto dto = new PasswordDto();
        dto.setOldPassword("OldP@ssw0rd");
        
        char[] passwordChars = dto.getOldPasswordChars();
        char[] expected = "OldP@ssw0rd".toCharArray();
        try {
            assertNotNull(passwordChars);
            assertArrayEquals(expected, passwordChars);
        } finally {
            PasswordSecurityUtil.clearPassword(expected);
        }
    }

    @Test
    void passwordDto_setOldPasswordChars_updatesStringPassword() {
        PasswordDto dto = new PasswordDto();
        char[] password = "OldP@ssw0rd".toCharArray();
        dto.setOldPasswordChars(password);
        
        assertEquals("OldP@ssw0rd", dto.getOldPassword());
    }

    @Test
    void passwordDto_clearPasswords_clearsArrays() {
        PasswordDto dto = new PasswordDto();
        char[] oldPassword = "OldP@ssw0rd".toCharArray();
        char[] newPassword = "NewP@ssw0rd".toCharArray();
        
        dto.setOldPasswordChars(oldPassword);
        dto.setNewPasswordChars(newPassword);
        
        dto.clearPasswords();
        
        // Verify arrays are cleared
        for (char c : oldPassword) {
            assertEquals('\0', c);
        }
        for (char c : newPassword) {
            assertEquals('\0', c);
        }
    }

    @Test
    void passwordDto_autoCloseable_clearsPasswordsOnClose() {
        PasswordDto dto = new PasswordDto();
        char[] oldPassword = "OldP@ssw0rd".toCharArray();
        char[] newPassword = "NewP@ssw0rd".toCharArray();
        
        dto.setOldPasswordChars(oldPassword);
        dto.setNewPasswordChars(newPassword);
        
        // Try-with-resources should call close()
        try (PasswordDto closeable = dto) {
            assertNotNull(closeable.getOldPassword());
            assertNotNull(closeable.getNewPassword());
        }
        
        // Verify passwords are cleared
        for (char c : oldPassword) {
            assertEquals('\0', c);
        }
        for (char c : newPassword) {
            assertEquals('\0', c);
        }
    }

    @Test
    void savePasswordDto_getNewPasswordChars_returnsCharArray() {
        SavePasswordDto dto = new SavePasswordDto();
        dto.setNewPassword("NewP@ssw0rd");
        
        char[] passwordChars = dto.getNewPasswordChars();
        char[] expected = "NewP@ssw0rd".toCharArray();
        try {
            assertNotNull(passwordChars);
            assertArrayEquals(expected, passwordChars);
        } finally {
            PasswordSecurityUtil.clearPassword(expected);
        }
    }

    @Test
    void savePasswordDto_setNewPasswordChars_updatesStringPassword() {
        SavePasswordDto dto = new SavePasswordDto();
        char[] password = "NewP@ssw0rd".toCharArray();
        dto.setNewPasswordChars(password);
        
        assertEquals("NewP@ssw0rd", dto.getNewPassword());
    }

    @Test
    void savePasswordDto_clearPasswords_clearsArrays() {
        SavePasswordDto dto = new SavePasswordDto();
        char[] newPassword = "NewP@ssw0rd".toCharArray();
        char[] confirmPassword = "NewP@ssw0rd".toCharArray();
        
        dto.setNewPasswordChars(newPassword);
        dto.setConfirmPasswordChars(confirmPassword);
        
        dto.clearPasswords();
        
        // Verify arrays are cleared
        for (char c : newPassword) {
            assertEquals('\0', c);
        }
        for (char c : confirmPassword) {
            assertEquals('\0', c);
        }
    }

    @Test
    void savePasswordDto_autoCloseable_clearsPasswordsOnClose() {
        SavePasswordDto dto = new SavePasswordDto();
        char[] newPassword = "NewP@ssw0rd".toCharArray();
        char[] confirmPassword = "NewP@ssw0rd".toCharArray();
        
        dto.setNewPasswordChars(newPassword);
        dto.setConfirmPasswordChars(confirmPassword);
        
        // Try-with-resources should call close()
        try (SavePasswordDto closeable = dto) {
            assertNotNull(closeable.getNewPassword());
            assertNotNull(closeable.getConfirmPassword());
        }
        
        // Verify passwords are cleared
        for (char c : newPassword) {
            assertEquals('\0', c);
        }
        for (char c : confirmPassword) {
            assertEquals('\0', c);
        }
    }

    @Test
    void integrationTest_fullPasswordFlow() {
        // Simulate a secure password handling flow
        UserDto userDto = new UserDto();
        
        // 1. Set password using char array
        char[] password = "SecureP@ssw0rd123".toCharArray();
        char[] matching = "SecureP@ssw0rd123".toCharArray();
        
        userDto.setPasswordChars(password);
        userDto.setMatchingPasswordChars(matching);
        
        // 2. Verify String fields are updated
        assertEquals("SecureP@ssw0rd123", userDto.getPassword());
        assertEquals("SecureP@ssw0rd123", userDto.getMatchingPassword());
        
        // 3. Clear passwords
        userDto.clearPasswords();
        
        // 4. Verify char arrays are cleared
        for (char c : password) {
            assertEquals('\0', c);
        }
        for (char c : matching) {
            assertEquals('\0', c);
        }
    }
}
