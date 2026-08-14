package com.digitalsanctuary.spring.user.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * Configuration properties for the password policy enforced by
 * {@link com.digitalsanctuary.spring.user.service.PasswordPolicyService}.
 *
 * <p>Bound from {@code user.security.password.*}. Defaults mirror the shipped
 * {@code config/dsspringuserconfig.properties} values.</p>
 */
@Data
@ConfigurationProperties(prefix = "user.security.password")
public class PasswordPolicyConfigProperties {

    /** Whether password-policy enforcement is active. */
    private boolean enabled = true;

    /** Minimum password length. */
    private int minLength = 8;

    /** Maximum password length. */
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
    private int historyCount = 3;

    /** Levenshtein similarity threshold (0-100) against username/email. */
    private int similarityThreshold = 70;
}
