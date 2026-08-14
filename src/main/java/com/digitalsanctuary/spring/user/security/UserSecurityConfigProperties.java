package com.digitalsanctuary.spring.user.security;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;
import lombok.ToString;

/**
 * Configuration properties for the flat {@code user.security.*} namespace: page/action URIs, URI lists, and
 * security scalars. Password policy and remember-me live in their own classes
 * ({@link PasswordPolicyConfigProperties}, {@link RememberMeConfigProperties}).
 *
 * <p>Defaults mirror the shipped {@code config/dsspringuserconfig.properties}. The camelCase key spellings
 * (e.g. {@code user.security.loginPageURI}) are canonical; relaxed binding also accepts kebab-case.</p>
 */
@Data
@ConfigurationProperties(prefix = "user.security")
public class UserSecurityConfigProperties {

    /** Default filter-chain action for URIs not otherwise matched: {@code deny} or {@code allow}. */
    private String defaultAction = "deny";
    /** Comma-delimited URIs protected by Spring Security when defaultAction is allow. */
    private List<String> protectedUris = new ArrayList<>(List.of("/protected.html"));
    /** Comma-delimited URIs not protected by Spring Security when defaultAction is deny. */
    private List<String> unprotectedUris =
            new ArrayList<>(List.of("/", "/index.html", "/favicon.ico", "/css/*", "/js/*", "/img/*",
                    "/user/registration", "/user/resendRegistrationToken", "/user/resetPassword",
                    "/user/registrationConfirm", "/user/changePassword", "/user/savePassword",
                    "/oauth2/authorization/*", "/login", "/error"));
    /** Comma-delimited URIs exempt from CSRF protection. Empty by default. */
    private List<String> disableCsrfUris = new ArrayList<>();

    /** The URI for the login page. */
    private String loginPageUri = "/user/login.html";
    /** The URI for the login action. */
    private String loginActionUri = "/user/login";
    /** The URI for the login success page. */
    private String loginSuccessUri = "/index.html?messageKey=message.login.success";
    /** The URI for the logout action. */
    private String logoutActionUri = "/user/logout";
    /** The URI for the logout success page. */
    private String logoutSuccessUri = "/index.html?messageKey=message.logout.success";
    /** Whether to always redirect to loginSuccessUri or use saved requests (default: false for better UX). */
    private boolean alwaysUseDefaultTargetUrl = false;
    /** The URI for the registration page. */
    private String registrationUri = "/user/register.html";
    /** The URI for the registration pending verification page. */
    private String registrationPendingUri = "/user/registration-pending-verification.html";
    /** The URI for the registration success page. */
    private String registrationSuccessUri = "/user/registration-complete.html";
    /** The URI for the request new verification email page. */
    private String registrationNewVerificationUri = "/user/request-new-verification-email.html";
    /** The URI for the registration confirm page. */
    private String registrationConfirmUri = "/user/registrationConfirm";
    /** The URI for the forgot password page. */
    private String forgotPasswordUri = "/user/forgot-password.html";
    /** The URI for the forgot password pending verification page. */
    private String forgotPasswordPendingUri = "/user/forgot-password-pending-verification.html";
    /** The URI for the forgot password change page. */
    private String forgotPasswordChangeUri = "/user/forgot-password-change.html";
    /** The URI for the update user page. */
    private String updateUserUri = "/user/update-user.html";
    /** The URI for the update password page. */
    private String updatePasswordUri = "/user/update-password.html";
    /** The URI for the delete account page. */
    private String deleteAccountUri = "/user/delete-account.html";
    /** The URI for the change password action. */
    private String changePasswordUri = "/user/changePassword";

    /** Password hash strength (bcrypt log rounds). */
    private int bcryptStrength = 12;
    /** Maximum failed login attempts before account lockout. */
    private int failedLoginAttempts = 10;
    /** Account lockout duration in minutes. */
    private int accountLockoutDuration = 30;
    /** Password reset token validity duration in minutes. */
    private int passwordResetTokenValidityMinutes = 1440;
    /** Whether to require canonical app URL for redirect validation. */
    private boolean requireCanonicalAppUrl = false;
    /** Whether to perform hash time tests during startup. */
    private boolean testHashTime = true;
    /** Whether to allow initial password set without step-up authentication. */
    private boolean allowInitialPasswordSetWithoutStepUp = false;
    /** Base application URL for redirect validation and email links. */
    private String appUrl = "";
    /** List of trusted hosts for redirect validation. */
    private List<String> trustedHosts = new ArrayList<>();

    /** HMAC secret used to hash password-reset tokens at rest. Excluded from {@code toString}. */
    @ToString.Exclude
    private String tokenHashSecret;

    public List<String> getProtectedUris() {
        return filterBlank(protectedUris);
    }

    public List<String> getUnprotectedUris() {
        return filterBlank(unprotectedUris);
    }

    public List<String> getDisableCsrfUris() {
        return filterBlank(disableCsrfUris);
    }

    private static List<String> filterBlank(List<String> values) {
        if (values == null) {
            return new ArrayList<>();
        }
        List<String> filtered = new ArrayList<>(values.size());
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                filtered.add(value.trim());
            }
        }
        return filtered;
    }
}
