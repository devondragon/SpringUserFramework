package com.digitalsanctuary.spring.user.security;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.ToString;

/**
 * Configuration properties for the flat {@code user.security.*} namespace: page/action URIs, URI lists, and
 * security scalars. Password policy and remember-me live in their own classes
 * ({@link PasswordPolicyConfigProperties}, {@link RememberMeConfigProperties}).
 *
 * <p>Defaults mirror the shipped {@code config/dsspringuserconfig.properties} where that file sets a value.
 * Use the camelCase key spellings (e.g. {@code user.security.loginPageURI}): relaxed binding also accepts
 * kebab-case for this bean, but the framework's {@code @GetMapping} placeholders resolve only the exact
 * camelCase key, so a kebab-only override would move the security configuration without moving the mapped
 * controller. {@link UriPlaceholderParityValidator} fails startup if the two ever diverge.</p>
 *
 * <p>The URI-list getters ({@code getProtectedUris()}, {@code getUnprotectedUris()}, {@code getDisableCsrfUris()},
 * {@code getTrustedHosts()}) return immutable, normalized copies: entries are trimmed and blank entries dropped,
 * and mutating the returned list throws rather than silently doing nothing.</p>
 *
 * <p>Range constraints (e.g. on {@code bcryptStrength}) are enforced at startup when a Bean Validation
 * implementation is on the classpath (e.g. {@code spring-boot-starter-validation}); without one they are
 * documentation only.</p>
 */
@Data
@Validated
@ConfigurationProperties(prefix = "user.security")
public class UserSecurityConfigProperties {

    /**
     * Default filter-chain action for URIs not otherwise matched: "deny" or "allow". Any other value fails
     * closed: the filter chain denies all requests and logs an error, forcing intentional configuration.
     */
    private String defaultAction = "deny";
    /** URIs protected by Spring Security when defaultAction is allow (comma-delimited in .properties files). */
    private List<String> protectedUris = new ArrayList<>(List.of("/protected.html"));
    /** URIs not protected by Spring Security when defaultAction is deny (comma-delimited in .properties files). */
    private List<String> unprotectedUris =
            new ArrayList<>(List.of("/", "/index.html", "/favicon.ico", "/css/*", "/js/*", "/img/*",
                    "/user/registration", "/user/resendRegistrationToken", "/user/resetPassword",
                    "/user/registrationConfirm", "/user/changePassword", "/user/savePassword",
                    "/oauth2/authorization/*", "/login", "/error"));
    /** URIs exempt from CSRF protection (comma-delimited in .properties files). Empty by default. */
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
    /** Whether to always redirect to loginSuccessUri after login instead of honoring the saved request. */
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

    /**
     * Whether to expose the page/action URIs (plus user.copyrightFirstYear) as the secret-free userSecurity
     * model attribute on controller requests, so templates can reference e.g. userSecurity.loginPageUri instead
     * of SpEL bean access, which Thymeleaf 3.1.5 forbids in restricted (layout-decorated) contexts. Set to false
     * to disable the advice entirely.
     */
    private boolean exposeUrisToModel = true;

    /** Password hash strength (bcrypt log rounds). Valid bcrypt range is 4-31. */
    @Min(4)
    @Max(31)
    private int bcryptStrength = 12;
    /** Maximum failed login attempts before account lockout. 0 disables lockout. */
    @Min(0)
    private int failedLoginAttempts = 10;
    /** Account lockout duration in minutes. 0 disables the lockout window; a negative value locks the account until an administrator unlocks it. */
    private int accountLockoutDuration = 30;
    /** Password reset token validity duration in minutes. */
    @Min(1)
    private int passwordResetTokenValidityMinutes = 1440;
    /**
     * When true, fail startup unless user.security.appUrl or a non-empty user.security.trustedHosts is
     * configured, so security email links (password reset, verification) can never derive their authority from a
     * spoofable Host header (CWE-640). When false (default), the library logs a startup warning instead of failing.
     */
    private boolean requireCanonicalAppUrl = false;
    /** Whether to perform hash time tests during startup. */
    private boolean testHashTime = true;
    /**
     * Controls the fallback behavior of POST /user/setPassword when no StepUpService bean is present. When false
     * (default), setting an initial password on a passwordless (passkey-only) account is disabled (HTTP 403)
     * unless a StepUpService is provided; set to true to explicitly allow the session-only behavior (SUF-02).
     */
    private boolean allowInitialPasswordSetWithoutStepUp = false;
    /**
     * Canonical base URL for security email links (password reset, verification). STRONGLY recommended in
     * production to prevent Host-header poisoning (CWE-640). When set, X-Forwarded-Host is ignored.
     */
    private String appUrl = "";
    /**
     * When user.security.appUrl is not set, X-Forwarded-Host and the ordinary request host are honored for
     * security email links only when they appear in this allow-list; a non-allow-listed host falls back to the
     * first entry (comma-delimited in .properties files).
     */
    private List<String> trustedHosts = new ArrayList<>();

    /**
     * Optional secret used to key the at-rest hashing (HMAC-SHA-256) of verification and password-reset tokens.
     * If unset, plain SHA-256 is used (adequate because the tokens are high-entropy); setting a secret adds
     * defense-in-depth against a database-only compromise. Excluded from toString output.
     */
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

    public List<String> getTrustedHosts() {
        return filterBlank(trustedHosts);
    }

    private static List<String> filterBlank(List<String> values) {
        if (values == null) {
            return List.of();
        }
        List<String> filtered = new ArrayList<>(values.size());
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                filtered.add(value.trim());
            }
        }
        return List.copyOf(filtered);
    }
}
