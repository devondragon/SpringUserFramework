package com.digitalsanctuary.spring.user.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import com.digitalsanctuary.spring.user.UserConfiguration;
import com.digitalsanctuary.spring.user.roles.RolesAndPrivilegesConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Auto-configuration that contributes the library's core, consumer-overridable security beans:
 * {@link PasswordEncoder}, {@link SessionRegistry}, {@link RoleHierarchy}, and {@link DaoAuthenticationProvider}.
 *
 * <p>
 * Each bean is guarded by {@link ConditionalOnMissingBean}, so a consuming application can fully replace any of them simply by defining their own bean
 * of the same type &mdash; for example, to swap {@code BCryptPasswordEncoder} for an Argon2 or delegating encoder, supply a different
 * {@link SessionRegistry} implementation, or provide a custom {@link RoleHierarchy}. When the consumer defines no such bean, the library's default
 * applies and behavior is unchanged.
 * </p>
 *
 * <p>
 * These beans live on an {@code @AutoConfiguration} class &mdash; rather than directly as {@code @Bean} methods on the component-scanned
 * {@link WebSecurityConfig} &mdash; precisely because {@code @ConditionalOnMissingBean} is only reliable on auto-configuration classes, which are
 * guaranteed to load AFTER user-defined bean definitions. Placing the conditional on a component-scanned {@code @Configuration} would evaluate it too
 * early and could suppress the consumer's override or cause a bean-definition conflict (the H8 finding). This mirrors the pattern established for the
 * library's {@link org.springframework.security.web.SecurityFilterChain} in {@link WebSecurityFilterChainAutoConfiguration}.
 * </p>
 *
 * <p>
 * {@code authProvider()} intentionally <b>receives</b> the effective {@link PasswordEncoder} as a method parameter rather than calling
 * {@code encoder()} directly. Because Spring proxies {@code @Configuration}/{@code @AutoConfiguration} classes, a self-call to {@code encoder()} would
 * always return the library's bean even when a consumer overrode the {@link PasswordEncoder}. Injecting the parameter lets Spring supply the consumer's
 * encoder when present, so the authentication provider honors the override.
 * </p>
 */
@Slf4j
@AutoConfiguration(after = UserConfiguration.class)
@RequiredArgsConstructor
public class UserSecurityBeansAutoConfiguration {

    private final UserDetailsService userDetailsService;
    private final RolesAndPrivilegesConfig rolesAndPrivilegesConfig;

    @Value("${user.security.bcryptStrength:10}")
    private int bcryptStrength;

    /**
     * Creates the library's default {@link PasswordEncoder}, a {@link BCryptPasswordEncoder} using the configured {@code user.security.bcryptStrength}.
     * Backs off entirely if the consuming application defines its own {@link PasswordEncoder}.
     *
     * @return the default {@link BCryptPasswordEncoder}
     */
    @Bean
    @ConditionalOnMissingBean(PasswordEncoder.class)
    public PasswordEncoder encoder() {
        return new BCryptPasswordEncoder(bcryptStrength);
    }

    /**
     * Creates the library's default {@link SessionRegistry}, a {@link SessionRegistryImpl}. Backs off entirely if the consuming application defines its
     * own {@link SessionRegistry}.
     *
     * @return the default {@link SessionRegistryImpl}
     */
    @Bean
    @ConditionalOnMissingBean(SessionRegistry.class)
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    /**
     * Creates the library's default {@link RoleHierarchy} from the {@code roleHierarchyString} in {@link RolesAndPrivilegesConfig}. Returns
     * {@code null} (no role hierarchy) when the configuration is absent or empty &mdash; preserving the historical behavior. Backs off entirely if the
     * consuming application defines its own {@link RoleHierarchy}.
     *
     * @return the configured {@link RoleHierarchyImpl}, or {@code null} when no hierarchy is configured
     */
    @Bean
    @ConditionalOnMissingBean(RoleHierarchy.class)
    public RoleHierarchy roleHierarchy() {
        if (rolesAndPrivilegesConfig == null) {
            log.error("UserSecurityBeansAutoConfiguration.roleHierarchy: rolesAndPrivilegesConfig is null!");
            return null;
        }
        if (rolesAndPrivilegesConfig.getRoleHierarchyString() == null) {
            log.error("UserSecurityBeansAutoConfiguration.roleHierarchy: rolesAndPrivilegesConfig.getRoleHierarchyString() is null!");
            return null;
        }
        RoleHierarchyImpl roleHierarchy = RoleHierarchyImpl.fromHierarchy(rolesAndPrivilegesConfig.getRoleHierarchyString());
        log.debug("UserSecurityBeansAutoConfiguration.roleHierarchy: roleHierarchy: {}", roleHierarchy.toString());
        return roleHierarchy;
    }

    /**
     * Creates the library's default {@link DaoAuthenticationProvider}, wiring in the {@link UserDetailsService} and the effective
     * {@link PasswordEncoder}. The encoder is received as a method parameter (not via a self-call to {@code encoder()}) so a consumer-supplied
     * {@link PasswordEncoder} is honored. Backs off entirely if the consuming application defines its own {@link DaoAuthenticationProvider}.
     *
     * @param passwordEncoder the effective {@link PasswordEncoder} (the consumer's bean if present, otherwise the library default)
     * @return the default {@link DaoAuthenticationProvider}
     */
    @Bean
    @ConditionalOnMissingBean(DaoAuthenticationProvider.class)
    public DaoAuthenticationProvider authProvider(PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder);
        return authProvider;
    }

    /**
     * Creates a {@link MethodSecurityExpressionHandler} wired with the configured {@link RoleHierarchy} so method
     * security annotations (e.g. {@code @PreAuthorize}) honor the role hierarchy. Declared {@code static} so it is
     * available to the method-security infrastructure during early initialization. Backs off entirely if the
     * consuming application defines its own {@link MethodSecurityExpressionHandler}.
     *
     * @param roleHierarchy the effective {@link RoleHierarchy} (may be {@code null} when none is configured)
     * @return the configured {@link MethodSecurityExpressionHandler}
     */
    @Bean
    @ConditionalOnMissingBean(MethodSecurityExpressionHandler.class)
    static MethodSecurityExpressionHandler methodSecurityExpressionHandler(RoleHierarchy roleHierarchy) {
        DefaultMethodSecurityExpressionHandler expressionHandler = new DefaultMethodSecurityExpressionHandler();
        expressionHandler.setRoleHierarchy(roleHierarchy);
        return expressionHandler;
    }

    /**
     * Creates the {@link HttpSessionEventPublisher} that bridges servlet {@code HttpSession} lifecycle events into
     * the Spring event system (required for {@link SessionRegistry}-based concurrent-session tracking). Backs off
     * entirely if the consuming application defines its own {@link HttpSessionEventPublisher}.
     *
     * @return the {@link HttpSessionEventPublisher}
     */
    @Bean
    @ConditionalOnMissingBean(HttpSessionEventPublisher.class)
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    /**
     * Publishes Spring Security authentication events to the application event system so listeners can react to
     * successful/failed authentication. Backs off entirely if the consuming application defines its own
     * {@link AuthenticationEventPublisher}.
     *
     * @param applicationEventPublisher the Spring {@link ApplicationEventPublisher}
     * @return the default {@link AuthenticationEventPublisher}
     */
    @Bean
    @ConditionalOnMissingBean(AuthenticationEventPublisher.class)
    public AuthenticationEventPublisher authenticationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        return new DefaultAuthenticationEventPublisher(applicationEventPublisher);
    }
}
