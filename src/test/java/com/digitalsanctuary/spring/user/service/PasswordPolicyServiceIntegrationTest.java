package com.digitalsanctuary.spring.user.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Integration test for PasswordPolicyService that tests with actual property loading.
 * This ensures the properties file is correctly formatted and parsed.
 */
@SpringBootTest
@TestPropertySource(locations = "classpath:config/dsspringuserconfig.properties")
class PasswordPolicyServiceIntegrationTest {

    @Autowired
    private PasswordPolicyService passwordPolicyService;

    private static final Locale LOCALE = Locale.ENGLISH;

    @Test
    void testSpecialCharactersFromPropertiesFile() {
        // Test that quotes are NOT required as special characters
        // This password has all requirements except quotes
        List<String> errors1 = passwordPolicyService.validate(null, "Password1!", null, LOCALE);
        assertTrue(errors1.isEmpty(), "Password with ! should be valid, but got errors: " + errors1);

        // Test various special characters from the configured list
        String[] validPasswords = {
            "Password1~",  // tilde
            "Password1!",  // exclamation
            "Password1@",  // at sign
            "Password1#",  // hash
            "Password1$",  // dollar
            "Password1%",  // percent
            "Password1^",  // caret
            "Password1&",  // ampersand
            "Password1*",  // asterisk
            "Password1(",  // left paren
            "Password1)",  // right paren
            "Password1_",  // underscore
            "Password1-",  // hyphen
            "Password1+",  // plus
            "Password1=",  // equals
            "Password1{",  // left brace
            "Password1}",  // right brace
            "Password1[",  // left bracket
            "Password1]",  // right bracket
            "Password1|",  // pipe
            "Password1\\", // backslash
            "Password1:",  // colon
            "Password1;",  // semicolon
            "Password1\"", // double quote (should work as special char, not required)
            "Password1'",  // single quote
            "Password1<",  // less than
            "Password1>",  // greater than
            "Password1,",  // comma
            "Password1.",  // period
            "Password1?",  // question mark
            "Password1/"   // forward slash
        };

        for (String password : validPasswords) {
            List<String> errors = passwordPolicyService.validate(null, password, null, LOCALE);
            assertTrue(errors.isEmpty(),
                "Password '" + password + "' should be valid, but got errors: " + errors);
        }

        // Test that password without any special character fails
        // Note: This may throw NoSuchMessageException in test context, but that's OK
        // We're testing that the validation logic works, not the message formatting
        try {
            List<String> errors2 = passwordPolicyService.validate(null, "Password1", null, LOCALE);
            assertFalse(errors2.isEmpty(), "Password without special character should fail");
            assertTrue(errors2.stream().anyMatch(e -> e.contains("INSUFFICIENT_SPECIAL") || e.contains("special")),
                "Should have special character error, but got: " + errors2);
        } catch (Exception e) {
            // If we get a NoSuchMessageException, it means the validation detected the missing special char
            // which is what we want to test
            assertTrue(e.getMessage().contains("INSUFFICIENT_SPECIAL"),
                "Exception should be about missing special character: " + e.getMessage());
        }
    }

    @Test
    void testQuotesNotRequiredAfterFix() {
        // This test specifically verifies that quotes are not required
        // A password without quotes but with other special chars should be valid
        List<String> errors = passwordPolicyService.validate(null, "Password1@", null, LOCALE);
        assertTrue(errors.isEmpty(),
            "Password without quotes should be valid when it has other special chars. Errors: " + errors);
    }
}