package com.digitalsanctuary.spring.user.security;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

/**
 * Fails application startup when a {@code user.security.*} URI diverges between the bound
 * {@link UserSecurityConfigProperties} bean and the exact camelCase Environment key the framework's
 * {@code @GetMapping} placeholders resolve (e.g. {@code @GetMapping("${user.security.loginPageURI:...}")}).
 *
 * <p>
 * The bean accepts relaxed spellings (kebab-case, environment variables), but request-mapping placeholders do
 * not — they resolve only the literal camelCase key. A kebab-only override therefore moves the security
 * configuration (filter chain, interceptors) without moving the mapped controller: for example
 * {@code user.security.change-password-uri} would relocate the password-reset security-headers interceptor while
 * the token-validation endpoint stays at its default path, silently serving the reset page without its headers.
 * Failing startup with the offending keys named turns that silent split into an explicit configuration error.
 * The fix is to use the camelCase spelling (canonical for {@code user.security.*}; see CONFIG.md).
 * </p>
 *
 * <p>
 * Deliberately {@code @PostConstruct} rather than a {@code ContextRefreshedEvent} listener, for the same reason
 * as {@code CaptchaStartupValidator}: context events may be published on executor threads where a thrown
 * exception is discarded, which would silently void the fail-startup guarantee.
 * </p>
 */
@Component
@RequiredArgsConstructor
public class UriPlaceholderParityValidator {

    /**
     * Every {@code user.security.*} key used as a request-mapping placeholder, mapped to the corresponding
     * {@link UserSecurityConfigProperties} getter. Package-visible so tests can verify this map stays in sync
     * with the {@code @GetMapping} annotations in {@code UserPageController} and {@code UserActionController}.
     */
    static final Map<String, Function<UserSecurityConfigProperties, String>> MAPPING_PLACEHOLDER_KEYS = createMappingKeys();

    private static Map<String, Function<UserSecurityConfigProperties, String>> createMappingKeys() {
        Map<String, Function<UserSecurityConfigProperties, String>> keys = new LinkedHashMap<>();
        keys.put("user.security.loginPageURI", UserSecurityConfigProperties::getLoginPageUri);
        keys.put("user.security.registrationURI", UserSecurityConfigProperties::getRegistrationUri);
        keys.put("user.security.registrationPendingURI", UserSecurityConfigProperties::getRegistrationPendingUri);
        keys.put("user.security.registrationSuccessURI", UserSecurityConfigProperties::getRegistrationSuccessUri);
        keys.put("user.security.registrationNewVerificationURI",
                UserSecurityConfigProperties::getRegistrationNewVerificationUri);
        keys.put("user.security.registrationConfirmURI", UserSecurityConfigProperties::getRegistrationConfirmUri);
        keys.put("user.security.forgotPasswordURI", UserSecurityConfigProperties::getForgotPasswordUri);
        keys.put("user.security.forgotPasswordPendingURI", UserSecurityConfigProperties::getForgotPasswordPendingUri);
        keys.put("user.security.forgotPasswordChangeURI", UserSecurityConfigProperties::getForgotPasswordChangeUri);
        keys.put("user.security.updateUserURI", UserSecurityConfigProperties::getUpdateUserUri);
        keys.put("user.security.updatePasswordURI", UserSecurityConfigProperties::getUpdatePasswordUri);
        keys.put("user.security.deleteAccountURI", UserSecurityConfigProperties::getDeleteAccountUri);
        keys.put("user.security.changePasswordURI", UserSecurityConfigProperties::getChangePasswordUri);
        return keys;
    }

    private final Environment environment;
    private final UserSecurityConfigProperties userSecurityConfig;

    /**
     * Compares each placeholder-resolved value against the bound bean value and fails startup on any mismatch.
     * When the Environment does not contain the camelCase key, the placeholder falls back to its inline default,
     * which equals the field initializer — so a fresh instance supplies the comparison fallback.
     */
    @PostConstruct
    public void validateUriPlaceholderParity() {
        UserSecurityConfigProperties defaults = new UserSecurityConfigProperties();
        List<String> mismatches = new ArrayList<>();
        MAPPING_PLACEHOLDER_KEYS.forEach((key, getter) -> {
            String placeholderValue = environment.getProperty(key, getter.apply(defaults));
            String beanValue = getter.apply(userSecurityConfig);
            if (!placeholderValue.equals(beanValue)) {
                mismatches.add(key + " resolves to '" + placeholderValue + "' for request mappings but the bound "
                        + "user.security configuration value is '" + beanValue + "'");
            }
        });
        if (!mismatches.isEmpty()) {
            throw new IllegalStateException("user.security URI configuration is split between spellings: "
                    + String.join("; ", mismatches) + ". This usually means the value was set with a kebab-case or "
                    + "environment-variable spelling, which the typed configuration accepts but request-mapping "
                    + "placeholders do not — the controller would stay on the default URI while the security "
                    + "configuration moves. Use the camelCase key spelling (e.g. user.security.loginPageURI) shown "
                    + "in CONFIG.md.");
        }
    }
}
