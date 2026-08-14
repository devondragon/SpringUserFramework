package com.digitalsanctuary.spring.user.security;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.DelegatingMissingAuthorityAccessDeniedHandler;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthenticationFilter;
import com.digitalsanctuary.spring.user.service.DSOAuth2UserService;
import com.digitalsanctuary.spring.user.service.DSOidcUserService;
import com.digitalsanctuary.spring.user.service.LoginSuccessService;
import com.digitalsanctuary.spring.user.service.LogoutSuccessService;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Builds the library's Spring Security filter chain: form login, logout, remember-me, session registry wiring,
 * CSRF exemptions, OAuth2/WebAuthn/MFA setup, and the authorization rules derived from
 * {@code user.security.defaultAction} and the protected/unprotected URI lists. All {@code user.security.*} values
 * are read from {@link UserSecurityConfigProperties} and {@link RememberMeConfigProperties}; the resulting chain
 * is exposed as a bean by {@link WebSecurityFilterChainAutoConfiguration}, which backs off when the consuming
 * application defines its own {@link SecurityFilterChain}.
 */
@Slf4j
@Data
@EqualsAndHashCode(callSuper = false)
@Configuration
@RequiredArgsConstructor
public class WebSecurityConfig {


	private static final String DEFAULT_ACTION_DENY = "deny";
	private static final String DEFAULT_ACTION_ALLOW = "allow";

	@Value("${spring.security.oauth2.enabled:false}")
	private boolean oauth2Enabled;

	@Value("${user.dev.auto-login-enabled:false}")
	private boolean devAutoLoginEnabled;

	private final AuthenticationEntryPoint authenticationEntryPoint;
	private final UserDetailsService userDetailsService;
	private final LoginSuccessService loginSuccessService;
	private final LogoutSuccessService logoutSuccessService;
	private final DSOAuth2UserService dsOAuth2UserService;
	private final DSOidcUserService dsOidcUserService;
	private final WebAuthnConfigProperties webAuthnConfigProperties;
	private final MfaConfigProperties mfaConfigProperties;
	private final Environment environment;
	private final ApplicationEventPublisher applicationEventPublisher;
	private final RequestCache requestCache;
	private final ObjectProvider<PersistentTokenRepository> persistentTokenRepositoryProvider;
	private final UserSecurityConfigProperties userSecurityConfig;
	private final RememberMeConfigProperties rememberMeConfig;

	/**
	 * Builds the library's security filter chain for Spring Security.
	 * <p>
	 * This method is invoked by {@link WebSecurityFilterChainAutoConfiguration}, which exposes the result as a {@link SecurityFilterChain} bean at a
	 * low precedence and backs off entirely when the consuming application defines its own {@link SecurityFilterChain}. It is intentionally NOT a
	 * {@code @Bean} method here: {@code @ConditionalOnMissingBean} is only reliable on auto-configuration classes (which load after user-defined
	 * beans), so the conditional/ordering lives on the auto-configuration class rather than on this component-scanned {@code @Configuration}.
	 * </p>
	 *
	 * @param http the HttpSecurity object
	 * @param sessionRegistry the SessionRegistry used to track active sessions
	 * @return the SecurityFilterChain object
	 * @throws Exception if there is an issue creating the SecurityFilterChain
	 */
	public SecurityFilterChain buildSecurityFilterChain(HttpSecurity http, SessionRegistry sessionRegistry) throws Exception {
		log.debug("WebSecurityConfig.configure: user.security.defaultAction: {}", userSecurityConfig.getDefaultAction());
		log.debug("WebSecurityConfig.configure: unprotectedURIs: {}", userSecurityConfig.getUnprotectedUris());
		List<String> unprotectedURIs = getUnprotectedURIsList();
		log.debug("WebSecurityConfig.configure: enhanced unprotectedURIs: {}", unprotectedURIs.toString());

		http.formLogin(formLogin -> formLogin.loginPage(userSecurityConfig.getLoginPageUri())
				.loginProcessingUrl(userSecurityConfig.getLoginActionUri()).successHandler(loginSuccessService).permitAll());

		// Always configure exception handling with the injected entry point (HTMX-aware by default)
		http.exceptionHandling(handling -> handling.authenticationEntryPoint(authenticationEntryPoint));

		// Use the hardened RequestCache (see UserSecurityBeansAutoConfiguration.requestCache()) so automatic browser
		// probes of protected URLs (e.g. Safari fetching /apple-touch-icon.png while the login page renders) cannot
		// overwrite the user's saved deep link and hijack the post-login redirect. Consumer-overridable via a
		// RequestCache bean.
		http.requestCache(cache -> cache.requestCache(requestCache));

		// Configure remember-me only if explicitly enabled and key is provided. With no PersistentTokenRepository bean
		// present this stays on Spring's hash-based TokenBasedRememberMeServices (no server-side state); when a
		// repository bean exists (e.g. the JdbcTokenRepositoryImpl enabled via user.security.rememberMe.usePersistentTokens,
		// or a consumer-defined bean) the configurer switches to persistent tokens, which SessionInvalidationService can revoke.
		String rememberMeKey = rememberMeConfig.getKey();
		if (rememberMeConfig.isEnabled() && rememberMeKey != null && !rememberMeKey.trim().isEmpty()) {
			http.rememberMe(rememberMe -> {
				rememberMe.key(rememberMeKey).userDetailsService(userDetailsService)
						.tokenValiditySeconds(rememberMeConfig.getTokenValiditySeconds())
						.rememberMeParameter(rememberMeConfig.getRememberMeParameter())
						.rememberMeCookieName(rememberMeConfig.getRememberMeCookieName());
				if (rememberMeConfig.getUseSecureCookie() != null) {
					rememberMe.useSecureCookie(rememberMeConfig.getUseSecureCookie());
				}
				PersistentTokenRepository tokenRepository = persistentTokenRepositoryProvider.getIfAvailable();
				if (tokenRepository != null) {
					rememberMe.tokenRepository(tokenRepository);
				} else if (rememberMeConfig.isUsePersistentTokens()) {
					log.warn("WebSecurityConfig: user.security.rememberMe.usePersistentTokens=true but no PersistentTokenRepository bean is "
							+ "present; falling back to hash-based remember-me tokens, which SessionInvalidationService cannot revoke.");
				}
			});
		} else if (rememberMeConfig.isEnabled()) {
			log.warn("WebSecurityConfig: user.security.rememberMe.enabled=true but no user.security.rememberMe.key is configured; "
					+ "remember-me is NOT configured. Set a stable signing key to activate it.");
		}

		// Use the LogoutSuccessService handler (instead of logoutSuccessUrl) so logout publishes an audit event.
		// The handler still redirects to logoutSuccessURI (see LogoutSuccessService.onLogoutSuccess).
		http.logout(logout -> logout.logoutUrl(userSecurityConfig.getLogoutActionUri()).logoutSuccessHandler(logoutSuccessService)
				.invalidateHttpSession(true).deleteCookies("JSESSIONID"));

		// Register sessions in the SessionRegistry so SessionInvalidationService and concurrent-session
		// features actually work. maximumSessions(-1) = unlimited concurrent sessions, but still tracked
		// in the registry. The SessionRegistry is injected (rather than calling the local bean method) so
		// consumers and tests can override it via a @Primary / @ConditionalOnMissingBean bean.
		http.sessionManagement(session -> session.maximumSessions(-1).sessionRegistry(sessionRegistry));

		// If we have URIs to disable CSRF validation on, do so here
		List<String> csrfIgnoreList = new ArrayList<>(userSecurityConfig.getDisableCsrfUris());
		if (devAutoLoginEnabled && environment.matchesProfiles("local")) {
			csrfIgnoreList.add("/dev/**");
		}
		if (!csrfIgnoreList.isEmpty()) {
			http.csrf(csrf -> {
				csrf.ignoringRequestMatchers(csrfIgnoreList.toArray(new String[0]));
			});
		}

		// Configure OAuth2 if enabled. This would be for things like Google and Facebook registration and login
		if (oauth2Enabled) {
			setupOAuth2(http);
		}

		// Configure WebAuthn (Passkey) if enabled
		if (webAuthnConfigProperties.isEnabled()) {
			setupWebAuthn(http);
		}

		// Configure MFA if enabled
		if (mfaConfigProperties.isEnabled()) {
			setupMfa(http);
		}

		// Configure authorization rules based on the default action
		String defaultAction = userSecurityConfig.getDefaultAction();
		if (DEFAULT_ACTION_DENY.equals(defaultAction)) {
			// Allow access to unprotected URIs and require authentication for all other requests
			http.authorizeHttpRequests(
					(authorize) -> authorize.requestMatchers(unprotectedURIs.toArray(new String[0])).permitAll().anyRequest().authenticated());
		} else if (DEFAULT_ACTION_ALLOW.equals(defaultAction)) {
			// Require authentication for protected URIs and allow access to all other requests
			http.authorizeHttpRequests((authorize) -> authorize
					.requestMatchers(userSecurityConfig.getProtectedUris().toArray(new String[0])).authenticated().anyRequest().permitAll());
		} else {
			// Log an error and deny access to all resources if the default action is not set correctly
			log.error(
					"WebSecurityConfig.configure: user.security.defaultAction must be set to either {} or {}!!!  Denying access to all resources to force intentional configuration.",
					DEFAULT_ACTION_ALLOW, DEFAULT_ACTION_DENY);
			http.authorizeHttpRequests((authorize) -> authorize.anyRequest().denyAll());
		}

		return http.build();
	}

	/**
	 * Setup OAuth2 specific configuration.
	 *
	 * @param http the http security object to configure
	 * @throws Exception the exception
	 */
	private void setupOAuth2(HttpSecurity http) throws Exception {
		// Entry point is handled globally in securityFilterChain via the injected authenticationEntryPoint bean.
		// The failure handler stores only a GENERIC message in the session for the UI (raw exception messages can
		// leak account emails from Locked/Disabled exceptions and the registered provider from conflict errors);
		// the real detail is logged server-side by the handler itself.
		String loginPageURI = userSecurityConfig.getLoginPageUri();
		http.oauth2Login(o -> o.loginPage(loginPageURI).successHandler(loginSuccessService)
				.failureHandler(new SanitizingOAuth2AuthenticationFailureHandler(loginPageURI)).userInfoEndpoint(userInfo -> {
					userInfo.userService(dsOAuth2UserService);
					userInfo.oidcUserService(dsOidcUserService);
				}));
	}

	/**
	 * Setup WebAuthn (Passkey) specific configuration.
	 *
	 * @param http the http security object to configure
	 * @throws Exception the exception
	 */
	private void setupWebAuthn(HttpSecurity http) throws Exception {
		Set<String> allowedOrigins = webAuthnConfigProperties.getAllowedOrigins();
		if (allowedOrigins == null) {
			allowedOrigins = Collections.emptySet();
		}
		Set<String> normalizedAllowedOrigins = allowedOrigins.stream().map(String::trim).filter(origin -> !origin.isEmpty())
				.collect(Collectors.toSet());

		log.debug("WebSecurityConfig.setupWebAuthn: rpId={}, rpName={}, allowedOrigins={}", webAuthnConfigProperties.getRpId(),
				webAuthnConfigProperties.getRpName(), normalizedAllowedOrigins);

		http.webAuthn(webAuthn -> webAuthn.rpName(webAuthnConfigProperties.getRpName()).rpId(webAuthnConfigProperties.getRpId())
				.allowedOrigins(normalizedAllowedOrigins)
				.withObjectPostProcessor(webAuthnSuccessHandlerPostProcessor()));
	}

	/**
	 * Setup MFA specific configuration.
	 * <p>
	 * Configures a {@link DelegatingMissingAuthorityAccessDeniedHandler} that redirects partially-authenticated users to
	 * the appropriate factor login page when they are missing a required factor authority.
	 * </p>
	 *
	 * @param http the http security object to configure
	 * @throws Exception the exception
	 */
	private void setupMfa(HttpSecurity http) throws Exception {
		DelegatingMissingAuthorityAccessDeniedHandler.Builder handlerBuilder =
				DelegatingMissingAuthorityAccessDeniedHandler.builder();

		Map<String, String> factorToUri = Map.of(
				"PASSWORD", mfaConfigProperties.getPasswordEntryPointUri(),
				"WEBAUTHN", mfaConfigProperties.getWebauthnEntryPointUri());

		for (String factor : mfaConfigProperties.getFactors()) {
			String authority = MfaConfiguration.mapFactorToAuthority(factor);
			String uri = factorToUri.get(factor.toUpperCase());
			if (authority != null && uri != null) {
				handlerBuilder.addEntryPointFor(new LoginUrlAuthenticationEntryPoint(uri), authority);
			}
		}

		DelegatingMissingAuthorityAccessDeniedHandler handler = handlerBuilder.build();
		http.exceptionHandling(handling -> handling.accessDeniedHandler(handler));
		log.info("MFA configured with access denied handler for factors: {}", mfaConfigProperties.getFactors());
	}

	/**
	 * Creates an ObjectPostProcessor that sets our custom WebAuthn success handler on the WebAuthnAuthenticationFilter.
	 *
	 * @return an ObjectPostProcessor that injects a custom authentication success handler
	 */
	private ObjectPostProcessor<WebAuthnAuthenticationFilter> webAuthnSuccessHandlerPostProcessor() {
		return new ObjectPostProcessor<WebAuthnAuthenticationFilter>() {
			@Override
			public <O extends WebAuthnAuthenticationFilter> O postProcess(O filter) {
				filter.setAuthenticationSuccessHandler(new WebAuthnAuthenticationSuccessHandler(userDetailsService, applicationEventPublisher));
				return filter;
			}
		};
	}

	// Commenting this out to try adding /error to the unprotected URIs list instead
	// @Bean
	// public WebSecurityCustomizer webSecurityCustomizer() {
	// // Ignore the error endpoint. This can get caught in the auth filter chain from a failed static asset request and cause a bad redirect on a
	// // successful auth
	// return (web) -> web.ignoring().requestMatchers("/error");
	// }

	private List<String> getUnprotectedURIsList() {
		// Add the required user pages and actions to the unprotected URIs from configuration
		List<String> unprotectedURIs = new ArrayList<String>();
		unprotectedURIs.addAll(userSecurityConfig.getUnprotectedUris());
		// Auto-unprotect the always-public paths that browsers and crawlers probe automatically without any markup
		// referencing them. Because this widens the permitAll surface for EVERY consumer (who never opted in), the
		// patterns are deliberately scoped to exactly the auto-probed paths, not a broad prefix:
		// - /apple-touch-icon*.png : Safari/iOS probes /apple-touch-icon.png, -precomposed, and sized variants
		//   (e.g. -120x120, -120x120-precomposed) on every page load; every one ends in .png. The .png anchor keeps a
		//   route like /apple-touch-icon-admin protected.
		// - /favicon.* : the browser auto-probe is /favicon.ico (the .EXT anchor also covers /favicon.svg|.png root
		//   probes while keeping a route like /favicon-report protected). Sized favicons (/favicon-32x32.png) are
		//   <link>-referenced assets, not auto-probes, and are served from the app's static path.
		// - /.well-known/** : RFC 8615 well-known URIs (security.txt, ACME challenges, passkey/associated-domain
		//   files) are public by design.
		// These are never sensitive, and leaving them protected means every probe 302s to the login page. The intent
		// mirrors the exclusion set in the hardened RequestCache (see UserSecurityBeansAutoConfiguration.requestCache()),
		// though that save-side heuristic may be broader since misclassifying there merely fails open (a request is not
		// saved) rather than granting access. Consumers no longer need to remember to list these manually.
		unprotectedURIs.add("/apple-touch-icon*.png");
		unprotectedURIs.add("/favicon.*");
		unprotectedURIs.add("/.well-known/**");
		unprotectedURIs.add(userSecurityConfig.getLoginPageUri());
		unprotectedURIs.add(userSecurityConfig.getLoginActionUri());
		unprotectedURIs.add(userSecurityConfig.getLogoutSuccessUri());
		unprotectedURIs.add(userSecurityConfig.getRegistrationUri());
		unprotectedURIs.add(userSecurityConfig.getRegistrationPendingUri());
		unprotectedURIs.add(userSecurityConfig.getRegistrationNewVerificationUri());
		unprotectedURIs.add(userSecurityConfig.getForgotPasswordUri());
		unprotectedURIs.add(userSecurityConfig.getRegistrationSuccessUri());
		unprotectedURIs.add(userSecurityConfig.getForgotPasswordPendingUri());
		unprotectedURIs.add(userSecurityConfig.getForgotPasswordChangeUri());
		if (devAutoLoginEnabled && environment.matchesProfiles("local")) {
			unprotectedURIs.add("/dev/**");
		}
		if (mfaConfigProperties.isEnabled()) {
			unprotectedURIs.add("/user/mfa/status");
			// A partially-authenticated user (one factor satisfied) is redirected to the configured factor
			// entry-point page(s) to complete the remaining factor(s). Those pages MUST be reachable without
			// full authentication; otherwise the redirect target is itself denied and the user loops between
			// entry points (ERR_TOO_MANY_REDIRECTS). Auto-unprotect the configured entry-point URIs so a
			// consuming app does not have to remember to list them manually.
			addIfHasText(unprotectedURIs, mfaConfigProperties.getPasswordEntryPointUri());
			addIfHasText(unprotectedURIs, mfaConfigProperties.getWebauthnEntryPointUri());
		}
		unprotectedURIs.removeAll(Collections.emptyList());
		return unprotectedURIs;
	}

	/**
	 * Adds the given URI to the list only when it is non-null and not blank.
	 *
	 * @param uris the list to add to
	 * @param uri the candidate URI (may be {@code null} or blank)
	 */
	private void addIfHasText(List<String> uris, String uri) {
		if (uri != null && !uri.isBlank()) {
			uris.add(uri);
		}
	}

}
