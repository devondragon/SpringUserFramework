package com.digitalsanctuary.spring.user.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.SecurityFilterChain;
import com.digitalsanctuary.spring.user.UserConfiguration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Auto-configuration that contributes the library's {@link SecurityFilterChain}.
 *
 * <p>
 * The chain is contributed at a low precedence ({@link #SECURITY_FILTER_CHAIN_ORDER}) so it acts as the catch-all chain. The back-off is keyed on the
 * bean <em>name</em> {@code securityFilterChain} (via {@link ConditionalOnMissingBean}), which supports two distinct consumer scenarios:
 * </p>
 * <ul>
 * <li><b>Add additional, narrower chains alongside the library's</b> (the common case). A consumer can define one or more extra
 * {@link SecurityFilterChain} beans with their own {@code @Order} and {@code securityMatcher} (e.g. an actuator-only or API-only chain). Because the
 * conditional is name-based, those differently-named chains do <em>not</em> suppress the library chain &mdash; both coexist, and Spring Security's
 * {@code FilterChainProxy} consults them in {@code @Order}. The narrower chain (higher precedence / lower order) handles its matched requests; the
 * library chain remains the catch-all for everything else (form login, logout, CSRF, session management, WebAuthn, OAuth2).</li>
 * <li><b>Fully replace the library's chain</b> by defining a {@link SecurityFilterChain} bean named exactly {@code securityFilterChain}. That single
 * named bean suppresses the library's chain, and the consumer then owns all security rules, including the library's protected URIs.</li>
 * </ul>
 *
 * <p>
 * <b>Why name-based rather than type-based:</b> a type-based {@code @ConditionalOnMissingBean(SecurityFilterChain.class)} would back off as soon as the
 * consumer defined <em>any</em> chain &mdash; even a narrow one &mdash; silently suppressing the entire library chain and leaving the library's URIs
 * unprotected. Keying on the {@code securityFilterChain} bean name preserves the standard Spring Security multi-chain {@code @Order} layering pattern,
 * while still giving consumers a clear, explicit way to opt into a full replacement (name your replacement bean {@code securityFilterChain}).
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
@EnableWebSecurity
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
    @ConditionalOnMissingBean(name = "securityFilterChain")
    public SecurityFilterChain securityFilterChain(HttpSecurity http, SessionRegistry sessionRegistry) throws Exception {
        log.debug("WebSecurityFilterChainAutoConfiguration: contributing library SecurityFilterChain at order {}", SECURITY_FILTER_CHAIN_ORDER);
        return webSecurityConfig.buildSecurityFilterChain(http, sessionRegistry);
    }
}
