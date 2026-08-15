package com.digitalsanctuary.spring.user.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.digitalsanctuary.spring.user.security.UserSecurityConfigProperties;

/**
 * Exposes {@link UserSecurityUriView} as the {@code userSecurity} model attribute so consuming templates read
 * framework URIs without SpEL bean access. Applies to every {@code @Controller} request — including
 * {@code @RestController} handlers, which are meta-annotated with {@code @Controller}; for those the model is
 * simply discarded with the response body unaffected. Registered by default; opt out with
 * {@code user.security.expose-uris-to-model=false} (bound as {@code exposeUrisToModel} on
 * {@link UserSecurityConfigProperties}). {@code userSecurity} is a reserved model-attribute name.
 */
@ConditionalOnProperty(name = "user.security.expose-uris-to-model", havingValue = "true", matchIfMissing = true)
@ControllerAdvice(annotations = Controller.class)
public class UserSecurityUriControllerAdvice {

    private final UserSecurityConfigProperties config;
    private final String copyrightFirstYear;

    /**
     * @param config the security URI configuration to read from
     * @param copyrightFirstYear the {@code user.copyrightFirstYear} value (outside {@code user.security}, so it is
     *        injected separately rather than sourced from {@code config})
     */
    public UserSecurityUriControllerAdvice(UserSecurityConfigProperties config,
            @Value("${user.copyrightFirstYear:}") String copyrightFirstYear) {
        this.config = config;
        this.copyrightFirstYear = copyrightFirstYear;
    }

    /**
     * @return the immutable URI view exposed to templates as {@code userSecurity}
     */
    @ModelAttribute("userSecurity")
    public UserSecurityUriView userSecurity() {
        return new UserSecurityUriView(config.getLoginPageUri(), config.getLoginActionUri(),
                config.getLoginSuccessUri(), config.getLogoutActionUri(), config.getLogoutSuccessUri(),
                config.getRegistrationUri(), config.getRegistrationPendingUri(), config.getRegistrationSuccessUri(),
                config.getRegistrationNewVerificationUri(), config.getRegistrationConfirmUri(),
                config.getForgotPasswordUri(), config.getForgotPasswordPendingUri(),
                config.getForgotPasswordChangeUri(), config.getUpdateUserUri(), config.getUpdatePasswordUri(),
                config.getDeleteAccountUri(), config.getChangePasswordUri(), copyrightFirstYear);
    }
}
