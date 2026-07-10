package com.digitalsanctuary.spring.user.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import lombok.RequiredArgsConstructor;

/**
 * Spring MVC configuration class that registers web interceptors for the user framework.
 *
 * <p>This configuration registers the {@link GlobalUserModelInterceptor} to handle
 * automatic user model injection for MVC controllers. The interceptor is applied to
 * all paths except static resources (CSS, JS, images, etc.).</p>
 *
 * @author Devon Hillard
 * @see GlobalUserModelInterceptor
 * @see org.springframework.web.servlet.config.annotation.WebMvcConfigurer
 */
@Configuration
@RequiredArgsConstructor
public class WebInterceptorConfig implements WebMvcConfigurer {

    private final GlobalUserModelInterceptor globalUserModelInterceptor;

    /** The password-reset token-validation endpoint; the reset token appears in its redirect URL. */
    @Value("${user.security.changePasswordURI:/user/changePassword}")
    private String changePasswordURI;

    /** The change-password page the reset flow redirects to; the reset token appears in its URL. */
    @Value("${user.security.forgotPasswordChangeURI:/user/forgot-password-change.html}")
    private String forgotPasswordChangeURI;

    /**
     * Add the global user model interceptor to the registry, plus the SUF-05 reset-page security-headers interceptor.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(globalUserModelInterceptor).addPathPatterns("/", "/**") // Apply to all paths
                .excludePathPatterns("/static/**", "/css/**", "/js/**", "/images/**", "/favicon.ico"); // Exclude static assets

        // SUF-05 (CWE-598): the reset flow carries the reset token in the page URL. Add Referrer-Policy: no-referrer and
        // Cache-Control: no-store to those pages so the token is not leaked via the Referer header or written to caches.
        registry.addInterceptor(new PasswordResetSecurityHeadersInterceptor())
                .addPathPatterns(changePasswordURI, forgotPasswordChangeURI);
    }
}
