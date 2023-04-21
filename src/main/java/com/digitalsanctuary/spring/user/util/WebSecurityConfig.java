package com.digitalsanctuary.spring.user.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.security.config.Customizer.withDefaults;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.expression.SecurityExpressionHandler;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.FilterInvocation;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.expression.DefaultWebSecurityExpressionHandler;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import com.digitalsanctuary.spring.user.service.DSOAuth2UserService;
import com.digitalsanctuary.spring.user.service.LoginSuccessService;
import com.digitalsanctuary.spring.user.service.LogoutSuccessService;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
@EqualsAndHashCode(callSuper = false)
@Configuration
@EnableWebSecurity
public class WebSecurityConfig {

	private static String DEFAULT_ACTION_DENY = "deny";
	private static String DEFAULT_ACTION_ALLOW = "allow";

	@Value("${user.security.defaultAction}")
	private String defaultAction;

	@Value("#{'${user.security.protectedURIs}'.split(',')}")
	private String[] protectedURIsArray;

	@Value("#{'${user.security.unprotectedURIs}'.split(',')}")
	private String[] unprotectedURIsArray;

	@Value("#{'${user.security.disableCSRFdURIs}'.split(',')}")
	private String[] disableCSRFURIsArray;

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

	@Autowired
	private UserDetailsService userDetailsService;

	@Autowired
	private LoginSuccessService loginSuccessService;

	@Autowired
	private LogoutSuccessService logoutSuccessService;

	@Autowired
	private RolesAndPrivilegesConfig rolesAndPrivilegesConfig;

	@Autowired
	private DSOAuth2UserService dsOAuth2UserService;


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
		log.debug("WebSecurityConfig.configure:" + "user.security.defaultAction: {}", getDefaultAction());
		log.debug("WebSecurityConfig.configure:" + "unprotectedURIs: {}", Arrays.toString(unprotectedURIsArray));
		ArrayList<String> unprotectedURIs = getUnprotectedURIsList();
		log.debug("WebSecurityConfig.configure:" + "enhanced unprotectedURIs: {}", unprotectedURIs.toString());

		CustomOAuth2AuthenticationEntryPoint loginAuthenticationEntryPoint = new CustomOAuth2AuthenticationEntryPoint(null, loginPageURI);

		List<String> disableCSRFURIs = Arrays.stream(disableCSRFURIsArray).filter(uri -> uri != null && !uri.isEmpty()).collect(Collectors.toList());

		http.formLogin(
				formLogin -> formLogin.loginPage(loginPageURI).loginProcessingUrl(loginActionURI).successHandler(loginSuccessService).permitAll())
				.rememberMe(withDefaults());

		http.logout(logout -> logout.logoutUrl(logoutActionURI).logoutSuccessUrl(logoutSuccessURI).invalidateHttpSession(true)
				.deleteCookies("JSESSIONID"));

		if (disableCSRFURIs != null && disableCSRFURIs.size() > 0) {
			http.csrf(csrf -> {
				csrf.ignoringRequestMatchers(disableCSRFURIsArray);
			});
		}
		http.oauth2Login(o -> o.loginPage(loginPageURI).successHandler(loginSuccessService).failureHandler((request, response, exception) -> {
			log.error("WebSecurityConfig.configure:" + "OAuth2 login failure: {}", exception.getMessage());
			request.getSession().setAttribute("error.message", exception.getMessage());
			response.sendRedirect(loginPageURI);
			// handler.onAuthenticationFailure(request, response, exception);
		}).userInfoEndpoint().userService(dsOAuth2UserService)).userDetailsService(userDetailsService)
				.exceptionHandling(handling -> handling.authenticationEntryPoint(loginAuthenticationEntryPoint));


		// Configure authorization rules based on the default action
		if (DEFAULT_ACTION_DENY.equals(getDefaultAction())) {
			// Allow access to unprotected URIs and require authentication for all other requests
			http.authorizeHttpRequests().requestMatchers(unprotectedURIs.toArray(new String[0])).permitAll().anyRequest().authenticated();
		} else if (DEFAULT_ACTION_ALLOW.equals(getDefaultAction())) {
			// Require authentication for protected URIs and allow access to all other requests
			http.authorizeHttpRequests().requestMatchers(protectedURIsArray).authenticated().requestMatchers("/**").permitAll();
		} else {
			// Log an error and deny access to all resources if the default action is not set correctly
			log.error("WebSecurityConfig.configure:"
					+ "user.security.defaultAction must be set to either {} or {}!!!  Denying access to all resources to force intentional configuration.",
					DEFAULT_ACTION_ALLOW, DEFAULT_ACTION_DENY);
			http.authorizeHttpRequests().anyRequest().denyAll();
		}
		return http.build();
	}

	private ArrayList<String> getUnprotectedURIsList() {
		// Add the required user pages and actions to the unprotectedURIsArray
		ArrayList<String> unprotectedURIs = new ArrayList<String>();
		unprotectedURIs.addAll(Arrays.asList(unprotectedURIsArray));
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
		unprotectedURIs.removeAll(Arrays.asList("", null));
		return unprotectedURIs;
	}

	@Bean
	public DaoAuthenticationProvider authProvider() {
		final DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
		authProvider.setUserDetailsService(userDetailsService);
		authProvider.setPasswordEncoder(encoder());
		return authProvider;
	}

	@Bean
	public PasswordEncoder encoder() {
		return new BCryptPasswordEncoder(16);
	}

	@Bean
	public SessionRegistry sessionRegistry() {
		return new SessionRegistryImpl();
	}

	@Bean
	public RoleHierarchy roleHierarchy() {
		RoleHierarchyImpl roleHierarchy = new RoleHierarchyImpl();
		roleHierarchy.setHierarchy(rolesAndPrivilegesConfig.getRoleHierarchyString());
		log.debug("WebSecurityConfig.roleHierarchy:" + "roleHierarchy: {}", roleHierarchy.toString());
		return roleHierarchy;
	}

	@Bean
	public SecurityExpressionHandler<FilterInvocation> webExpressionHandler() {
		DefaultWebSecurityExpressionHandler defaultWebSecurityExpressionHandler = new DefaultWebSecurityExpressionHandler();
		defaultWebSecurityExpressionHandler.setRoleHierarchy(roleHierarchy());
		return defaultWebSecurityExpressionHandler;
	}

	@Bean
	public HttpSessionEventPublisher httpSessionEventPublisher() {
		return new HttpSessionEventPublisher();
	}

}
