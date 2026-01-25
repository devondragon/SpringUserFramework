package com.digitalsanctuary.spring.user.validation;

import com.digitalsanctuary.spring.user.dto.UserDto;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validator implementation for the {@link PasswordMatches} constraint annotation.
 * Validates that the password and matchingPassword fields in a {@link UserDto} are equal.
 *
 * <p>Default constructor creates an instance of this validator.</p>
 *
 * @see PasswordMatches
 * @see UserDto
 */
public class PasswordMatchesValidator implements ConstraintValidator<PasswordMatches, Object> {
    
    /**
     * Initializes the validator.
     * 
     * @param constraintAnnotation the annotation instance
     */
    @Override
    public void initialize(PasswordMatches constraintAnnotation) {
        // No initialization needed
    }
    
    /**
     * Validates that the password and matchingPassword fields match.
     * 
     * @param obj the object to validate
     * @param context the constraint validator context
     * @return true if the passwords match or if either field is null (to allow other validators to handle null checks),
     *         false otherwise
     */
    @Override
    public boolean isValid(Object obj, ConstraintValidatorContext context) {
        if (!(obj instanceof UserDto)) {
            // If not a UserDto, consider it valid (this validator doesn't apply)
            return true;
        }
        
        UserDto user = (UserDto) obj;
        
        // If either password is null, let the @NotBlank validators handle it
        if (user.getPassword() == null || user.getMatchingPassword() == null) {
            return true;
        }
        
        return user.getPassword().equals(user.getMatchingPassword());
    }
}