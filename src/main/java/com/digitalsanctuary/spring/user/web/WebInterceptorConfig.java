package com.digitalsanctuary.spring.user.web;

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

    /**
     * Add the global user model interceptor to the registry
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(globalUserModelInterceptor).addPathPatterns("/", "/**") // Apply to all paths
                .excludePathPatterns("/static/**", "/css/**", "/js/**", "/images/**", "/favicon.ico"); // Exclude static assets
    }
}
