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

    // --- Access control ---
    /** Default filter-chain action for URIs not otherwise matched: {@code deny} or {@code allow}. */
    private String defaultAction = "deny";
    private List<String> protectedUris = new ArrayList<>();
    private List<String> unprotectedUris = new ArrayList<>();
    private List<String> disableCsrfUris = new ArrayList<>();

    // --- Page / action URIs ---
    private String loginPageUri = "/user/login.html";
    private String loginActionUri = "/user/login";
    private String loginSuccessUri = "/index.html?messageKey=message.login.success";
    private String logoutActionUri = "/user/logout";
    private String logoutSuccessUri = "/index.html?messageKey=message.logout.success";
    private boolean alwaysUseDefaultTargetUrl = false;
    private String registrationUri = "/user/register.html";
    private String registrationPendingUri = "/user/registration-pending-verification.html";
    private String registrationSuccessUri = "/user/registration-complete.html";
    private String registrationNewVerificationUri = "/user/request-new-verification-email.html";
    private String registrationConfirmUri = "/user/registrationConfirm";
    private String forgotPasswordUri = "/user/forgot-password.html";
    private String forgotPasswordPendingUri = "/user/forgot-password-pending-verification.html";
    private String forgotPasswordChangeUri = "/user/forgot-password-change.html";
    private String updateUserUri = "/user/update-user.html";
    private String updatePasswordUri = "/user/update-password.html";
    private String deleteAccountUri = "/user/delete-account.html";
    private String changePasswordUri = "/user/changePassword";

    // --- Security scalars ---
    private int bcryptStrength = 12;
    private int failedLoginAttempts = 10;
    private int accountLockoutDuration = 30;
    private int passwordResetTokenValidityMinutes = 1440;
    private boolean requireCanonicalAppUrl = false;
    private boolean testHashTime = true;
    private boolean allowInitialPasswordSetWithoutStepUp = false;
    private String appUrl = "";
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
