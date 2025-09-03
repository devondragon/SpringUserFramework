package com.digitalsanctuary.spring.user.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Validation annotation to verify that the password and matchingPassword fields match.
 * This is a class-level constraint that should be applied to DTOs containing password confirmation fields.
 */
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordMatchesValidator.class)
@Documented
public @interface PasswordMatches {
    
    /**
     * The error message to display when passwords do not match.
     * 
     * @return the error message
     */
    String message() default "Passwords do not match";
    
    /**
     * Validation groups.
     * 
     * @return the groups
     */
    Class<?>[] groups() default {};
    
    /**
     * Additional payload data.
     * 
     * @return the payload
     */
    Class<? extends Payload>[] payload() default {};
}