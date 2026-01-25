package com.digitalsanctuary.spring.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Data Transfer Object for initiating password reset requests.
 * <p>
 * Contains only the email field needed to start the password reset flow.
 * The email is validated for format and length constraints.
 * </p>
 *
 * @author Digital Sanctuary
 */
@Data
public class PasswordResetRequestDto {
    
    /** The email address for password reset */
    @NotBlank(message = "Email is required")
    @Email(message = "Please provide a valid email address")
    @Size(max = 100, message = "Email must not exceed 100 characters")
    private String email;
}