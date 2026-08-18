package com.digitalsanctuary.spring.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Data Transfer Object for resending a registration verification email.
 * <p>
 * Contains only the email address needed to resend the verification email. Binding this instead of
 * the registration {@link UserDto} keeps the endpoint from requiring name and password fields a
 * resend request has no reason to carry.
 * </p>
 *
 * @author Devon Hillard
 */
@Data
public class ResendVerificationDto {

    /** The email address to resend the verification email to. */
    @NotBlank(message = "Email is required")
    @Email(message = "Please provide a valid email address")
    @Size(max = 100, message = "Email must not exceed 100 characters")
    private String email;
}
