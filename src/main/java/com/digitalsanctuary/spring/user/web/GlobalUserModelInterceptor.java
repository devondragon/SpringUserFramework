package com.digitalsanctuary.spring.user.web;


import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
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
 * Spring MVC interceptor that conditionally adds the authenticated user to the model.
 *
 * <p>This interceptor supports two modes of operation controlled by
 * {@link UserWebConfig#globalUserModelOptIn}:</p>
 *
 * <ul>
 *   <li><strong>Opt-In Mode (default, globalUserModelOptIn=false):</strong> User is only added
 *       to the model for controllers/methods annotated with {@link IncludeUserInModel}.</li>
 *   <li><strong>Opt-Out Mode (globalUserModelOptIn=true):</strong> User is added to all views
 *       by default, except those annotated with {@link ExcludeUserFromModel}.</li>
 * </ul>
 *
 * <p>The user object is retrieved from the Spring Security context and added to the model
 * as the {@code user} attribute when applicable.</p>
 *
 * @author Digital Sanctuary
 * @see UserWebConfig
 * @see IncludeUserInModel
 * @see ExcludeUserFromModel
 * @see org.springframework.web.servlet.HandlerInterceptor
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
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) throws Exception {
        // Allow all requests to proceed by default
        log.debug("Handling request for path: {}", request.getRequestURI());

        return true;
    }


    /**
     * Post-handle method to add the current user to the model for applicable requests.
     */
    @Override
    public void postHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler,
            @Nullable ModelAndView modelAndView) throws Exception {
        log.debug("handler is: {}", handler.getClass().getName());
        log.debug("modelAndView: {}", modelAndView);
        if (modelAndView == null || !(handler instanceof HandlerMethod)) {
            return;
        }

        HandlerMethod handlerMethod = (HandlerMethod) handler;

        // Apply global user model injection behavior based on configuration
        if (userWebConfig.isGlobalUserModelOptIn()) {
            // Global Opt-In Mode (globalUserModelOptIn=true):
            // - User is added to ALL views by default
            // - Only skip if @ExcludeUserFromModel is present
            if (hasAnnotation(handlerMethod, ExcludeUserFromModel.class)) {
                return; // Skip - explicitly excluded
            }
        } else {
            // Global Opt-Out Mode (globalUserModelOptIn=false) - DEFAULT:
            // - User is NOT added to any views by default
            // - Only add if @IncludeUserInModel is present
            if (!hasAnnotation(handlerMethod, IncludeUserInModel.class)) {
                return; // Skip - not explicitly included
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
