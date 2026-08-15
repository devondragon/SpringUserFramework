package com.digitalsanctuary.spring.user.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * Configuration properties for the password policy enforced by
 * {@link com.digitalsanctuary.spring.user.service.PasswordPolicyService}.
 *
 * <p>Bound from {@code user.security.password.*}. Defaults mirror the shipped
 * {@code config/dsspringuserconfig.properties} values.</p>
 *
 * <p>Cross-field invariants ({@code minLength <= maxLength}, a non-empty {@code specialChars} set when
 * {@code requireSpecial=true}, {@code similarityThreshold} in 0-100) are enforced at startup when a Bean
 * Validation implementation is on the classpath (e.g. {@code spring-boot-starter-validation}), turning an
 * impossible policy into a named configuration error instead of every registration failing at runtime.</p>
 */
@Data
@Validated
@ConfigurationProperties(prefix = "user.security.password")
public class PasswordPolicyConfigProperties {

    /** Whether password-policy enforcement is active. */
    private boolean enabled = true;

    /** Minimum password length. */
    @Min(1)
    private int minLength = 8;

    /** Maximum password length. */
    @Min(1)
    private int maxLength = 128;

    /** Whether at least one uppercase character is required. */
    private boolean requireUppercase = true;

    /** Whether at least one lowercase character is required. */
    private boolean requireLowercase = true;

    /** Whether at least one digit is required. */
    private boolean requireDigit = true;

    /** Whether at least one special character is required. */
    private boolean requireSpecial = true;

    /** The set of characters treated as "special". */
    private String specialChars = "~`!@#$%^&*()_-+={}[]|\\:;\"'<>,.?/";

    /** Whether passwords are checked against the common-passwords dictionary. */
    private boolean preventCommonPasswords = true;

    /** Number of previous passwords retained and rejected on reuse. */
    @Min(0)
    private int historyCount = 3;

    /** Levenshtein similarity threshold (0-100) against username/email. */
    @Min(0)
    @Max(100)
    private int similarityThreshold = 70;

    @AssertTrue(message = "user.security.password.minLength must be less than or equal to maxLength")
    private boolean isLengthRangeValid() {
        return minLength <= maxLength;
    }

    @AssertTrue(message = "user.security.password.specialChars must not be empty when requireSpecial=true")
    private boolean isSpecialCharsUsable() {
        return !requireSpecial || (specialChars != null && !specialChars.isEmpty());
    }
}
