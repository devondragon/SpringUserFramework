package com.digitalsanctuary.spring.user.util;

import java.util.ArrayList;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.digitalsanctuary.spring.user.service.LoginSuccessService;
import com.digitalsanctuary.spring.user.service.LogoutSuccessService;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true, securedEnabled = true, jsr250Enabled = true)
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {

	private static String DEFAULT_ACTION_DENY = "deny";
	private static String DEFAULT_ACTION_ALLOW = "allow";

	public Logger logger = LoggerFactory.getLogger(this.getClass());

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

	@Bean
	@Override
	public AuthenticationManager authenticationManagerBean() throws Exception {
		return super.authenticationManagerBean();
	}

	@Override
	protected void configure(final AuthenticationManagerBuilder auth) throws Exception {
		auth.authenticationProvider(authProvider());
	}

	@Override
	protected void configure(HttpSecurity http) throws Exception {
		logger.debug("WebSecurityConfig.configure:" + "user.security.defaultAction: {}", getDefaultAction());
		logger.debug("WebSecurityConfig.configure:" + "unprotectedURIs: {}", Arrays.toString(unprotectedURIsArray));

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
		unprotectedURIs.add(forgotPasswordPendingURI);
		unprotectedURIs.add(forgotPasswordChangeURI);
		unprotectedURIs.removeAll(Arrays.asList("", null));

		logger.debug("WebSecurityConfig.configure:" + "enhanced unprotectedURIs: {}", unprotectedURIs.toString());

		ArrayList<String> disableCSRFURIs = new ArrayList<String>();
		disableCSRFURIs.addAll(Arrays.asList(disableCSRFURIsArray));
		disableCSRFURIs.removeAll(Arrays.asList("", null));

		if (DEFAULT_ACTION_DENY.equals(getDefaultAction())) {
			http.authorizeRequests().antMatchers(unprotectedURIs.toArray(new String[0])).permitAll().anyRequest()
					.authenticated().and().formLogin().loginPage(loginPageURI).loginProcessingUrl(loginActionURI)
					.successHandler(loginSuccessService).permitAll().and().logout().logoutUrl(logoutActionURI)
					.invalidateHttpSession(true).logoutSuccessHandler(logoutSuccessService).deleteCookies("JSESSIONID")
					.permitAll();
			if (disableCSRFURIs != null && disableCSRFURIs.size() > 0) {
				http.csrf().ignoringAntMatchers(disableCSRFURIs.toArray(new String[0]));
			}
		} else if (DEFAULT_ACTION_ALLOW.equals(getDefaultAction())) {
			http.authorizeRequests().antMatchers(protectedURIsArray).authenticated().antMatchers("/**").permitAll()
					.and().formLogin().loginPage(loginPageURI).loginProcessingUrl(loginActionURI)
					.successHandler(loginSuccessService).and().logout()
					.logoutUrl(logoutActionURI).invalidateHttpSession(true).logoutSuccessHandler(logoutSuccessService)
					.deleteCookies("JSESSIONID").permitAll();

			if (disableCSRFURIs != null && disableCSRFURIs.size() > 0) {
				http.csrf().ignoringAntMatchers(disableCSRFURIs.toArray(new String[0]));
			}
		} else {
			logger.error("WebSecurityConfig.configure:"
					+ "user.security.defaultAction must be set to either {} or {}!!!  Denying access to all resources to force intentional configuration.",
					DEFAULT_ACTION_ALLOW, DEFAULT_ACTION_DENY);
			http.authorizeRequests().anyRequest().denyAll();
		}

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
		return new BCryptPasswordEncoder(11);
	}

	@Bean
	public SessionRegistry sessionRegistry() {
		return new SessionRegistryImpl();
	}
}