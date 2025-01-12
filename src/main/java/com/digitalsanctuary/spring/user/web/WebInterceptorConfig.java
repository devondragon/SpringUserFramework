package com.digitalsanctuary.spring.user.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import lombok.RequiredArgsConstructor;

/**
 * Web configuration class for setting up interceptors
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
