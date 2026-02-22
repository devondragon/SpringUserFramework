package com.digitalsanctuary.spring.user.security;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import com.digitalsanctuary.spring.user.roles.RolesAndPrivilegesConfig;
import com.digitalsanctuary.spring.user.service.DSOAuth2UserService;
import com.digitalsanctuary.spring.user.service.DSOidcUserService;
import com.digitalsanctuary.spring.user.service.LoginSuccessService;
import com.digitalsanctuary.spring.user.service.LogoutSuccessService;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The WebSecurityConfig class is a Spring Boot configuration class that provides properties for configuring the web security. This class is used to
 * define properties that control the behavior of the web security, such as the default action for protected URIs and the URIs that are protected or
 * unprotected.
 */
@Slf4j
@Data
@EqualsAndHashCode(callSuper = false)
@Configuration
@RequiredArgsConstructor
@EnableWebSecurity
public class WebSecurityConfig {


	private static final String DEFAULT_ACTION_DENY = "deny";
	private static final String DEFAULT_ACTION_ALLOW = "allow";

	@Value("${user.security.defaultAction}")
	private String defaultAction;

	@Value("${user.security.protectedURIs}")
	private String protectedURIsProperty;

	@Value("${user.security.unprotectedURIs}")
	private String unprotectedURIsProperty;

	@Value("${user.security.disableCSRFURIs}")
	private String disableCSRFURIsProperty;

	@Value("${user.security.loginPageURI}")
	private String loginPageURI;

	@Value("${user.security.loginActionURI}")
	private String loginActionURI;

	@Value("${user.security.loginSuccessURI}")
	private String loginSuccessURI;

	@Value("${user.security.logoutActionURI}")
	private String logoutActionURI;

	@Value("${user.security.logoutSuccessURI}")
	private String logoutSuccessURI;

	@Value("${user.security.registrationURI}")
	private String registrationURI;

	@Value("${user.security.registrationPendingURI}")
	private String registrationPendingURI;

	@Value("${user.security.registrationSuccessURI}")
	private String registrationSuccessURI;

	@Value("${user.security.forgotPasswordURI}")
	private String forgotPasswordURI;

	@Value("${user.security.forgotPasswordPendingURI}")
	private String forgotPasswordPendingURI;

	@Value("${user.security.forgotPasswordChangeURI}")
	private String forgotPasswordChangeURI;

	@Value("${user.security.registrationNewVerificationURI}")
	private String registrationNewVerificationURI;

	@Value("${spring.security.oauth2.enabled:false}")
	private boolean oauth2Enabled;

	@Value("${user.security.bcryptStrength}")
	private int bcryptStrength = 10;

	@Value("${user.security.rememberMe.enabled:false}")
	private boolean rememberMeEnabled;

	@Value("${user.security.rememberMe.key:#{null}}")
	private String rememberMeKey;

	@Value("${user.dev.auto-login-enabled:false}")
	private boolean devAutoLoginEnabled;

	private final UserDetailsService userDetailsService;
	private final LoginSuccessService loginSuccessService;
	private final LogoutSuccessService logoutSuccessService;
	private final RolesAndPrivilegesConfig rolesAndPrivilegesConfig;
	private final DSOAuth2UserService dsOAuth2UserService;
	private final DSOidcUserService dsOidcUserService;
	private final WebAuthnConfigProperties webAuthnConfigProperties;
	private final Environment environment;

	/**
	 *
	 * The securityFilterChain method builds the security filter chain for Spring Security.
	 *
	 * @param http the HttpSecurity object
	 * @return the SecurityFilterChain object
	 * @throws Exception if there is an issue creating the SecurityFilterChain
	 */
	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		log.debug("WebSecurityConfig.configure: user.security.defaultAction: {}", getDefaultAction());
		log.debug("WebSecurityConfig.configure: unprotectedURIs: {}", Arrays.toString(getUnprotectedURIsArray()));
		List<String> unprotectedURIs = getUnprotectedURIsList();
		log.debug("WebSecurityConfig.configure: enhanced unprotectedURIs: {}", unprotectedURIs.toString());

		http.formLogin(
				formLogin -> formLogin.loginPage(loginPageURI).loginProcessingUrl(loginActionURI).successHandler(loginSuccessService).permitAll());

		// Configure remember-me only if explicitly enabled and key is provided
		if (rememberMeEnabled && rememberMeKey != null && !rememberMeKey.trim().isEmpty()) {
			http.rememberMe(rememberMe -> rememberMe.key(rememberMeKey).userDetailsService(userDetailsService));
		}

		http.logout(logout -> logout.logoutUrl(logoutActionURI).logoutSuccessUrl(logoutSuccessURI).invalidateHttpSession(true)
				.deleteCookies("JSESSIONID"));

		// If we have URIs to disable CSRF validation on, do so here
		String[] baseDisableCSRFURIs = getDisableCSRFURIsArray();
		List<String> csrfIgnoreList = new ArrayList<>(Arrays.asList(baseDisableCSRFURIs));
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

		// Configure authorization rules based on the default action
		if (DEFAULT_ACTION_DENY.equals(getDefaultAction())) {
			// Allow access to unprotected URIs and require authentication for all other requests
			http.authorizeHttpRequests(
					(authorize) -> authorize.requestMatchers(unprotectedURIs.toArray(new String[0])).permitAll().anyRequest().authenticated());
		} else if (DEFAULT_ACTION_ALLOW.equals(getDefaultAction())) {
			// Require authentication for protected URIs and allow access to all other requests
			http.authorizeHttpRequests((authorize) -> authorize.requestMatchers(getProtectedURIsArray()).authenticated().anyRequest().permitAll());
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
		CustomOAuth2AuthenticationEntryPoint loginAuthenticationEntryPoint = new CustomOAuth2AuthenticationEntryPoint(null, loginPageURI);

		http.exceptionHandling(handling -> handling.authenticationEntryPoint(loginAuthenticationEntryPoint))
				.oauth2Login(o -> o.loginPage(loginPageURI).successHandler(loginSuccessService).failureHandler((request, response, exception) -> {
					log.error("WebSecurityConfig.configure: OAuth2 login failure: {}", exception.getMessage());
					request.getSession().setAttribute("error.message", exception.getMessage());
					response.sendRedirect(loginPageURI);
					// handler.onAuthenticationFailure(request, response, exception);
				}).userInfoEndpoint(userInfo -> {
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
				.withObjectPostProcessor(
						new org.springframework.security.config.ObjectPostProcessor<org.springframework.security.web.webauthn.authentication.WebAuthnAuthenticationFilter>() {
							@Override
							public <O extends org.springframework.security.web.webauthn.authentication.WebAuthnAuthenticationFilter> O postProcess(
									O filter) {
								filter.setAuthenticationSuccessHandler(new WebAuthnAuthenticationSuccessHandler(userDetailsService));
								return filter;
							}
						}));
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
		unprotectedURIs.addAll(Arrays.asList(getUnprotectedURIsArray()));
		unprotectedURIs.add(loginPageURI);
		unprotectedURIs.add(loginActionURI);
		unprotectedURIs.add(logoutSuccessURI);
		unprotectedURIs.add(registrationURI);
		unprotectedURIs.add(registrationPendingURI);
		unprotectedURIs.add(registrationNewVerificationURI);
		unprotectedURIs.add(forgotPasswordURI);
		unprotectedURIs.add(registrationSuccessURI);
		unprotectedURIs.add(forgotPasswordPendingURI);
		unprotectedURIs.add(forgotPasswordChangeURI);
		if (devAutoLoginEnabled && environment.matchesProfiles("local")) {
			unprotectedURIs.add("/dev/**");
		}
		unprotectedURIs.removeAll(Collections.emptyList());
		return unprotectedURIs;
	}

	/**
	 * The authProvider method creates a DaoAuthenticationProvider and sets the UserDetailsService and PasswordEncoder for the provider.
	 *
	 * @return the DaoAuthenticationProvider object
	 */
	@Bean
	public DaoAuthenticationProvider authProvider() {
		DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);
		authProvider.setPasswordEncoder(encoder());
		return authProvider;
	}

	/**
	 * The encoder method creates a BCryptPasswordEncoder with the bcryptStrength value.
	 *
	 * @return the BCryptPasswordEncoder object
	 */
	@Bean
	public PasswordEncoder encoder() {
		return new BCryptPasswordEncoder(bcryptStrength);
	}

	/**
	 * The sessionRegistry method creates a SessionRegistryImpl object.
	 *
	 * @return the SessionRegistryImpl object
	 */
	@Bean
	public SessionRegistry sessionRegistry() {
		return new SessionRegistryImpl();
	}

	/**
	 * The roleHierarchy method creates a RoleHierarchyImpl object from the roleHierarchyString in the rolesAndPrivilegesConfig object.
	 *
	 * @return the RoleHierarchyImpl object
	 */
	@Bean
	public RoleHierarchy roleHierarchy() {
		if (rolesAndPrivilegesConfig == null) {
			log.error("WebSecurityConfig.roleHierarchy: rolesAndPrivilegesConfig is null!");
			return null;
		}
		if (rolesAndPrivilegesConfig.getRoleHierarchyString() == null) {
			log.error("WebSecurityConfig.roleHierarchy: rolesAndPrivilegesConfig.getRoleHierarchyString() is null!");
			return null;
		}
		RoleHierarchyImpl roleHierarchy = RoleHierarchyImpl.fromHierarchy(rolesAndPrivilegesConfig.getRoleHierarchyString());
		log.debug("WebSecurityConfig.roleHierarchy: roleHierarchy: {}", roleHierarchy.toString());
		return roleHierarchy;
	}

	/**
	 * The methodSecurityExpressionHandler method creates a MethodSecurityExpressionHandler object and sets the roleHierarchy for the handler. This
	 * ensures that method security annotations like @PreAuthorize use the configured role hierarchy.
	 *
	 * @return the MethodSecurityExpressionHandler object
	 */
	@Bean
	static MethodSecurityExpressionHandler methodSecurityExpressionHandler(RoleHierarchy roleHierarchy) {
		DefaultMethodSecurityExpressionHandler expressionHandler = new DefaultMethodSecurityExpressionHandler();
		expressionHandler.setRoleHierarchy(roleHierarchy);
		return expressionHandler;
	}

	/**
	 * The httpSessionEventPublisher method creates an HttpSessionEventPublisher object.
	 *
	 * @return the HttpSessionEventPublisher object
	 */
	@Bean
	public HttpSessionEventPublisher httpSessionEventPublisher() {
		return new HttpSessionEventPublisher();
	}

	/**
	 * This is required to publish authentication events to the Spring event system. This allows us to listen for authentication events and perform
	 * actions based on successful or failed authentication.
	 *
	 * @param applicationEventPublisher the Spring ApplicationEventPublisher
	 * @return the Spring Security default AuthenticationEventPublisher
	 */
	@Bean
	public AuthenticationEventPublisher authenticationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
		return new DefaultAuthenticationEventPublisher(applicationEventPublisher);
	}

	/**
	 * Helper method to split comma-separated property values and filter out empty strings.
	 *
	 * @param property the comma-separated property value
	 * @return array of non-empty strings
	 */
	private String[] splitAndFilterProperty(String property) {
		if (property == null || property.trim().isEmpty()) {
			return new String[0];
		}
		return Arrays.stream(property.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new);
	}

	/**
	 * Get the protected URIs array with empty values filtered out.
	 *
	 * @return array of protected URI patterns
	 */
	private String[] getProtectedURIsArray() {
		return splitAndFilterProperty(protectedURIsProperty);
	}

	/**
	 * Get the unprotected URIs array with empty values filtered out.
	 *
	 * @return array of unprotected URI patterns
	 */
	private String[] getUnprotectedURIsArray() {
		return splitAndFilterProperty(unprotectedURIsProperty);
	}

	/**
	 * Get the disable CSRF URIs array with empty values filtered out.
	 *
	 * @return array of URI patterns to disable CSRF protection for
	 */
	private String[] getDisableCSRFURIsArray() {
		return splitAndFilterProperty(disableCSRFURIsProperty);
	}

}
