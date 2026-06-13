package com.digitalsanctuary.spring.user.profile.session;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.core.annotation.AliasFor;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

/**
 * Convenience meta-annotation that registers a Spring bean as a correctly session-scoped component.
 *
 * <p>
 * Spring's {@link Scope @Scope} annotation is <strong>not inherited</strong> by subclasses. A concrete subclass of
 * {@link BaseSessionProfile} that is annotated only with {@code @Component} (and no {@code @Scope}) becomes a
 * <strong>singleton shared across every HTTP session</strong> &mdash; one user's profile data leaks to all other
 * users. To avoid this trap, every concrete session profile must declare session scoping on itself.
 * </p>
 *
 * <p>
 * Annotating a subclass with {@code @SessionScopedProfile} is the single-annotation equivalent of writing both:
 * </p>
 *
 * <pre>{@code
 * @Component
 * @Scope(value = WebApplicationContext.SCOPE_SESSION, proxyMode = ScopedProxyMode.TARGET_CLASS)
 * }</pre>
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>{@code
 * @SessionScopedProfile
 * public class CustomSessionProfile extends BaseSessionProfile<CustomUserProfile> {
 *     // ...
 * }
 * }</pre>
 *
 * @see BaseSessionProfile
 * @see Scope
 * @see WebApplicationContext#SCOPE_SESSION
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
@Scope(value = WebApplicationContext.SCOPE_SESSION, proxyMode = ScopedProxyMode.TARGET_CLASS)
public @interface SessionScopedProfile {

    /**
     * Alias for {@link Component#value()} to allow the bean name to be supplied directly on the meta-annotation.
     *
     * @return the suggested component name, if any
     */
    @AliasFor(annotation = Component.class)
    String value() default "";
}
