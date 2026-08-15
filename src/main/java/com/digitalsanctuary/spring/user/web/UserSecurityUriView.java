package com.digitalsanctuary.spring.user.web;

/**
 * Immutable, secret-free view of the {@code user.security.*} URIs (plus the copyright first year) for templates.
 * Exposed as the {@code userSecurity} model attribute so views reference e.g. {@code ${userSecurity.loginPageUri}}
 * instead of SpEL bean access, which Thymeleaf 3.1.5 forbids in restricted (layout-decorated) contexts.
 */
public record UserSecurityUriView(String loginPageUri, String loginActionUri, String loginSuccessUri,
        String logoutActionUri, String logoutSuccessUri, String registrationUri, String registrationPendingUri,
        String registrationSuccessUri, String registrationNewVerificationUri, String registrationConfirmUri,
        String forgotPasswordUri, String forgotPasswordPendingUri, String forgotPasswordChangeUri,
        String updateUserUri, String updatePasswordUri, String deleteAccountUri, String changePasswordUri,
        String copyrightFirstYear) {
}
