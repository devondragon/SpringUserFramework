package com.digitalsanctuary.spring.user.web;


import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import com.digitalsanctuary.spring.user.service.DSUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Interceptor to add the current user to the model for applicable requests.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GlobalUserModelInterceptor implements HandlerInterceptor {

    // The UserWebConfig object is used to determine the global user model opt-in behavior
    private final UserWebConfig userWebConfig;

    /**
     * Pre-handle method to allow all requests to proceed by default.
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Allow all requests to proceed by default
        log.debug("Handling request for path: {}", request.getRequestURI());

        return true;
    }


    /**
     * Post-handle method to add the current user to the model for applicable requests.
     */
    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        log.debug("handler is: {}", handler.getClass().getName());
        log.debug("modelAndView: {}", modelAndView);
        if (modelAndView == null || !(handler instanceof HandlerMethod)) {
            return;
        }

        HandlerMethod handlerMethod = (HandlerMethod) handler;

        // Apply global opt-in or opt-out behavior
        if (userWebConfig.isGlobalUserModelOptIn()) {
            // Global Opt-In Mode: Skip if not explicitly opted-in
            if (!hasAnnotation(handlerMethod, IncludeUserInModel.class)) {
                return; // Skip if not explicitly opted-in
            }
        } else {
            // Global Opt-Out Mode: Skip if explicitly excluded
            if (hasAnnotation(handlerMethod, ExcludeUserFromModel.class)) {
                return; // Skip if explicitly excluded
            }
        }

        // Add user to the model if applicable
        log.debug("GlobalUserModelInterceptor.postHandle: Adding user to model");


        // Retrieve the authenticated user from the security context
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            return;
        }
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (principal instanceof DSUserDetails userDetails) {
            modelAndView.addObject("user", userDetails.getUser());
        }

    }

    /**
     * Helper method to determine if the specified annotation is present on the handler method or controller class.
     */
    private boolean hasAnnotation(HandlerMethod handlerMethod, Class<? extends java.lang.annotation.Annotation> annotationClass) {
        // Check for the annotation on the method
        if (handlerMethod.getMethodAnnotation(annotationClass) != null) {
            return true;
        }

        // Check for the annotation on the controller class
        return handlerMethod.getBeanType().isAnnotationPresent(annotationClass);
    }

}
