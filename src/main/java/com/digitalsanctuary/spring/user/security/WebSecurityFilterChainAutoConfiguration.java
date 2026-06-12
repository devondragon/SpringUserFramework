package com.digitalsanctuary.spring.user.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.SecurityFilterChain;
import com.digitalsanctuary.spring.user.UserConfiguration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Auto-configuration that contributes the library's {@link SecurityFilterChain}.
 *
 * <p>
 * The chain is contributed at a low precedence ({@link #SECURITY_FILTER_CHAIN_ORDER}) and backs off entirely via {@link ConditionalOnMissingBean}
 * when the consuming application defines its own {@link SecurityFilterChain}. This lets consumers either:
 * </p>
 * <ul>
 * <li><b>Rely on the library's chain</b> (the default), or</li>
 * <li><b>Fully replace it</b> by defining their own {@link SecurityFilterChain} bean &mdash; in which case the library's chain is suppressed entirely
 * and the consumer owns all security rules, including the library's protected URIs.</li>
 * </ul>
 * <p>
 * A consumer that wants to layer additional rules in front of the library's chain can define their own chain with a higher-precedence (lower)
 * {@code @Order} value; that chain is then consulted first by Spring Security's {@code FilterChainProxy}.
 * </p>
 *
 * <p>
 * The actual chain-building logic lives in {@link WebSecurityConfig#buildSecurityFilterChain(HttpSecurity, SessionRegistry)}. It is exposed via this
 * auto-configuration (rather than directly as a {@code @Bean} on {@link WebSecurityConfig}) because {@code @ConditionalOnMissingBean} is only reliable
 * on auto-configuration classes, which are guaranteed to load after any user-defined bean definitions. Placing the conditional on a
 * component-scanned {@code @Configuration} bean method would evaluate it too early and could suppress the library chain incorrectly.
 * </p>
 */
@Slf4j
@AutoConfiguration(after = UserConfiguration.class)
@RequiredArgsConstructor
public class WebSecurityFilterChainAutoConfiguration {

    /**
     * Order of the library's {@link SecurityFilterChain}. This is a low precedence (high numeric value) so that any consumer-supplied chain with a
     * lower {@code @Order} takes precedence. The value is sourced from {@link SecurityFilterProperties#BASIC_AUTH_ORDER}
     * ({@code Ordered.LOWEST_PRECEDENCE - 5}), so the library's chain sits at the same low precedence as Spring Boot's own default servlet security
     * chain and always loses to consumer chains. This constant was historically exposed as {@code SecurityProperties.BASIC_AUTH_ORDER} in Spring Boot
     * 3.x; in Spring Boot 4.0 it was relocated to {@link SecurityFilterProperties#BASIC_AUTH_ORDER} (still {@code Ordered.LOWEST_PRECEDENCE - 5}).
     */
    public static final int SECURITY_FILTER_CHAIN_ORDER = SecurityFilterProperties.BASIC_AUTH_ORDER;

    private final WebSecurityConfig webSecurityConfig;

    /**
     * Exposes the library's {@link SecurityFilterChain} bean, delegating construction to
     * {@link WebSecurityConfig#buildSecurityFilterChain(HttpSecurity, SessionRegistry)}.
     *
     * @param http the shared {@link HttpSecurity} builder
     * @param sessionRegistry the {@link SessionRegistry} used to track active sessions
     * @return the library's configured {@link SecurityFilterChain}
     * @throws Exception if there is an issue creating the {@link SecurityFilterChain}
     */
    @Bean
    @Order(SECURITY_FILTER_CHAIN_ORDER)
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain securityFilterChain(HttpSecurity http, SessionRegistry sessionRegistry) throws Exception {
        log.debug("WebSecurityFilterChainAutoConfiguration: contributing library SecurityFilterChain at order {}", SECURITY_FILTER_CHAIN_ORDER);
        return webSecurityConfig.buildSecurityFilterChain(http, sessionRegistry);
    }
}
