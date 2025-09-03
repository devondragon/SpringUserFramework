package com.digitalsanctuary.spring.user.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.digitalsanctuary.spring.user.dto.UserDto;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Unit tests for PasswordMatchesValidator.
 */
@DisplayName("PasswordMatchesValidator Tests")
class PasswordMatchesValidatorTest {
    
    private PasswordMatchesValidator validator;
    
    @Mock
    private ConstraintValidatorContext context;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        validator = new PasswordMatchesValidator();
        validator.initialize(null);
    }
    
    @Test
    @DisplayName("Should return true when passwords match")
    void testPasswordsMatch() {
        UserDto userDto = new UserDto();
        userDto.setPassword("SecurePass123!");
        userDto.setMatchingPassword("SecurePass123!");
        
        boolean result = validator.isValid(userDto, context);
        
        assertTrue(result, "Validator should return true when passwords match");
    }
    
    @Test
    @DisplayName("Should return false when passwords do not match")
    void testPasswordsDoNotMatch() {
        UserDto userDto = new UserDto();
        userDto.setPassword("SecurePass123!");
        userDto.setMatchingPassword("DifferentPass456!");
        
        boolean result = validator.isValid(userDto, context);
        
        assertFalse(result, "Validator should return false when passwords do not match");
    }
    
    @Test
    @DisplayName("Should return true when password is null (let other validators handle)")
    void testPasswordIsNull() {
        UserDto userDto = new UserDto();
        userDto.setPassword(null);
        userDto.setMatchingPassword("SecurePass123!");
        
        boolean result = validator.isValid(userDto, context);
        
        assertTrue(result, "Validator should return true when password is null to let @NotBlank handle it");
    }
    
    @Test
    @DisplayName("Should return true when matching password is null (let other validators handle)")
    void testMatchingPasswordIsNull() {
        UserDto userDto = new UserDto();
        userDto.setPassword("SecurePass123!");
        userDto.setMatchingPassword(null);
        
        boolean result = validator.isValid(userDto, context);
        
        assertTrue(result, "Validator should return true when matching password is null to let @NotBlank handle it");
    }
    
    @Test
    @DisplayName("Should return true when both passwords are null")
    void testBothPasswordsAreNull() {
        UserDto userDto = new UserDto();
        userDto.setPassword(null);
        userDto.setMatchingPassword(null);
        
        boolean result = validator.isValid(userDto, context);
        
        assertTrue(result, "Validator should return true when both passwords are null");
    }
    
    @Test
    @DisplayName("Should return true for non-UserDto objects")
    void testNonUserDtoObject() {
        String notAUserDto = "Not a UserDto";
        
        boolean result = validator.isValid(notAUserDto, context);
        
        assertTrue(result, "Validator should return true for non-UserDto objects");
    }
    
    @Test
    @DisplayName("Should handle empty strings correctly")
    void testEmptyStrings() {
        UserDto userDto = new UserDto();
        userDto.setPassword("");
        userDto.setMatchingPassword("");
        
        boolean result = validator.isValid(userDto, context);
        
        assertTrue(result, "Validator should return true when both passwords are empty strings (they match)");
    }
    
    @Test
    @DisplayName("Should be case sensitive")
    void testCaseSensitive() {
        UserDto userDto = new UserDto();
        userDto.setPassword("Password123");
        userDto.setMatchingPassword("password123");
        
        boolean result = validator.isValid(userDto, context);
        
        assertFalse(result, "Validator should be case sensitive");
    }
}