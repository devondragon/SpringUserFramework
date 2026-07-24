package com.digitalsanctuary.spring.user.security;

import java.util.List;
import java.util.Set;
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
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.RequestHeaderRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import jakarta.servlet.http.HttpServletRequest;
import com.digitalsanctuary.spring.user.UserConfiguration;
import com.digitalsanctuary.spring.user.roles.RolesAndPrivilegesConfig;
import com.digitalsanctuary.spring.user.util.AppUrlResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Auto-configuration that contributes the library's core, consumer-overridable security beans:
 * {@link PasswordEncoder}, {@link SessionRegistry}, {@link RoleHierarchy}, {@link DaoAuthenticationProvider}, and the hardened
 * {@link org.springframework.security.web.savedrequest.RequestCache}.
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

    /**
     * File extensions that identify static-asset fetches. Requests for these are never worth replaying after login, and browsers frequently fetch
     * them automatically (icons, manifests, fonts) while the login page itself is rendering &mdash; which would otherwise overwrite the user's real
     * saved destination.
     */
    private static final Set<String> STATIC_ASSET_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "ico", "svg", "webp", "avif", "css", "js", "mjs",
            "map", "woff", "woff2", "ttf", "otf", "eot", "webmanifest", "json", "xml");

    /**
     * Root-level paths that browsers and crawlers probe automatically without any markup referencing them. Safari/iOS in particular requests
     * {@code /apple-touch-icon.png} and {@code /apple-touch-icon-precomposed.png} on every page load.
     */
    private static final Set<String> AUTO_PROBED_PATH_PREFIXES = Set.of("/apple-touch-icon", "/favicon", "/.well-known/");

    /**
     * Creates the library's default {@link RequestCache}: an {@link HttpSessionRequestCache} that only saves requests that plausibly represent a
     * user navigation worth returning to after login &mdash; {@code GET} requests that explicitly accept {@code text/html} and are not XHR
     * ({@code X-Requested-With: XMLHttpRequest}), HTMX ({@code HX-Request}) or static-asset/auto-probed requests (favicons, apple-touch icons,
     * {@code /.well-known/**}, common static file extensions).
     *
     * <p>
     * Why this matters: Spring Security's default request cache saves <em>any</em> request that triggers authentication. Browsers automatically
     * probe protected-by-default URLs such as {@code /apple-touch-icon.png} while the login page renders, overwriting the saved deep link the user
     * actually clicked &mdash; so the post-login redirect lands on {@code /apple-touch-icon.png?continue} (typically a 404/error page) instead of
     * the user's destination. The hardened matcher makes the saved request survive those probes.
     * </p>
     *
     * <p>
     * Backs off entirely if the consuming application defines its own {@link RequestCache} bean, which is also the hook for consumers who want
     * Spring Security's default behavior back ({@code new HttpSessionRequestCache()}).
     * </p>
     *
     * @return the hardened {@link HttpSessionRequestCache}
     */
    @Bean
    @ConditionalOnMissingBean(RequestCache.class)
    public RequestCache requestCache() {
        MediaTypeRequestMatcher acceptsHtml = new MediaTypeRequestMatcher(MediaType.TEXT_HTML);
        // Treat "Accept: */*" as NOT an HTML navigation: real browser navigations always list text/html explicitly,
        // while auto-probes (touch icons, prefetchers) and API clients typically send */* or a concrete non-HTML type.
        acceptsHtml.setIgnoredMediaTypes(Set.of(MediaType.ALL));

        RequestMatcher isGet = request -> "GET".equalsIgnoreCase(request.getMethod());
        RequestMatcher isStaticAssetOrProbe = UserSecurityBeansAutoConfiguration::isStaticAssetOrAutoProbe;

        RequestMatcher savableNavigation = new AndRequestMatcher(isGet, acceptsHtml,
                new NegatedRequestMatcher(new RequestHeaderRequestMatcher("X-Requested-With", "XMLHttpRequest")),
                new NegatedRequestMatcher(new RequestHeaderRequestMatcher("HX-Request")), new NegatedRequestMatcher(isStaticAssetOrProbe));

        HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
        requestCache.setRequestMatcher(savableNavigation);
        return requestCache;
    }

    /**
     * Returns true when the request targets a static asset or a path that browsers/crawlers probe automatically (and which should therefore never
     * become a post-login redirect target).
     *
     * @param request the request to classify
     * @return true when the request is a static-asset fetch or well-known auto-probe
     */
    private static boolean isStaticAssetOrAutoProbe(HttpServletRequest request) {
        // getRequestURI() excludes the query string but is URL-encoded and unnormalized. That is acceptable here: this
        // is a fail-open save-side heuristic (misclassification at worst saves an odd redirect target), never an
        // authorization decision, so an encoded dot (%2E) slipping past extension detection has no security impact.
        String path = request.getRequestURI().substring(request.getContextPath().length()).toLowerCase();
        for (String prefix : AUTO_PROBED_PATH_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        int lastDot = path.lastIndexOf('.');
        int lastSlash = path.lastIndexOf('/');
        if (lastDot > lastSlash) {
            String extension = path.substring(lastDot + 1);
            // .html/.htm are real pages, everything else in the static set is an asset fetch
            return STATIC_ASSET_EXTENSIONS.contains(extension);
        }
        return false;
    }

    /**
     * Creates the library's default {@link AppUrlResolver}, which builds the base URL for security-sensitive email links (password reset, email
     * verification) from the configured canonical URL ({@code user.security.appUrl}) and/or the trusted-host allow-list
     * ({@code user.security.trustedHosts}), defending against Host-header / X-Forwarded-Host poisoning (CWE-640). Backs off entirely if the consuming
     * application defines its own {@link AppUrlResolver}.
     *
     * <p>
     * When neither {@code user.security.appUrl} nor {@code user.security.trustedHosts} is configured, email links derive their authority from the
     * request {@code Host} header, which can be spoofed (CWE-640). By default the library logs a startup warning in that case; setting
     * {@code user.security.requireCanonicalAppUrl=true} makes it fail startup instead, so an operator who wants a hard guarantee can opt into fail-fast.
     * </p>
     *
     * @param appUrl the configured canonical base URL, or {@code null} when unset
     * @param trustedHosts the allow-listed hosts (empty when unset)
     * @param requireCanonicalAppUrl when {@code true}, fail startup unless {@code appUrl} or a non-empty {@code trustedHosts} is configured
     * @return the default {@link AppUrlResolver}
     */
    @Bean
    @ConditionalOnMissingBean(AppUrlResolver.class)
    public AppUrlResolver appUrlResolver(@Value("${user.security.appUrl:#{null}}") String appUrl,
            @Value("${user.security.trustedHosts:}") List<String> trustedHosts,
            @Value("${user.security.requireCanonicalAppUrl:false}") boolean requireCanonicalAppUrl) {
        boolean appUrlConfigured = appUrl != null && !appUrl.isBlank();
        boolean trustedHostsConfigured = trustedHosts != null && trustedHosts.stream().anyMatch(h -> h != null && !h.isBlank());
        if (!appUrlConfigured && !trustedHostsConfigured) {
            if (requireCanonicalAppUrl) {
                throw new IllegalStateException("user.security.requireCanonicalAppUrl is enabled but neither user.security.appUrl nor "
                        + "user.security.trustedHosts is configured. Set a canonical user.security.appUrl (recommended) or a non-empty "
                        + "user.security.trustedHosts so password-reset and verification links cannot be poisoned via the Host header (CWE-640).");
            }
            log.warn("AppUrlResolver: neither user.security.appUrl nor user.security.trustedHosts is configured; password-reset and verification "
                    + "email links will derive their authority from the request Host header, which can be spoofed (CWE-640). Set "
                    + "user.security.appUrl to a canonical URL (recommended) or user.security.trustedHosts to close this exposure, or set "
                    + "user.security.requireCanonicalAppUrl=true to fail startup instead of warning.");
        }
        return new AppUrlResolver(appUrl, trustedHosts);
    }
}
