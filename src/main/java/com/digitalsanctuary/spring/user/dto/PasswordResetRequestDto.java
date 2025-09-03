package com.digitalsanctuary.spring.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO for password reset requests. Contains only the email field needed for initiating a password reset.
 */
@Data
public class PasswordResetRequestDto {
    
    /** The email address for password reset */
    @NotBlank(message = "Email is required")
    @Email(message = "Please provide a valid email address")
    @Size(max = 100, message = "Email must not exceed 100 characters")
    private String email;
}