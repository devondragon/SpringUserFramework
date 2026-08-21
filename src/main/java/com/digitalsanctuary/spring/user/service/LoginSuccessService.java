package com.digitalsanctuary.spring.user.service;

import java.io.IOException;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.stereotype.Service;
import org.thymeleaf.util.StringUtils;
import com.digitalsanctuary.spring.user.audit.AuditEvent;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.security.UserSecurityConfigProperties;
import com.digitalsanctuary.spring.user.util.UserUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Service that handles successful user authentication events.
 *
 * <p>Extends {@link SavedRequestAwareAuthenticationSuccessHandler} to provide custom
 * post-authentication processing including audit event publishing and redirect handling.
 * Configurable via {@code user.security.loginSuccessURI} and
 * {@code user.security.alwaysUseDefaultTargetUrl} properties.</p>
 *
 * @author Devon Hillard
 * @see SavedRequestAwareAuthenticationSuccessHandler
 */
@Slf4j
@Service
public class LoginSuccessService extends SavedRequestAwareAuthenticationSuccessHandler {

	/** The event publisher. */
	private final ApplicationEventPublisher eventPublisher;

	/** Writes the replaced context back; the filter already saved the original before this handler runs. */
	private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

	/** The user security configuration properties. */
	private final UserSecurityConfigProperties userSecurityConfig;

	/**
	 * Constructs the login success handler and wires in the application's effective {@link RequestCache}.
	 *
	 * <p>
	 * The injected {@code requestCache} is passed to {@link #setRequestCache(RequestCache)} so this handler <em>reads</em> the saved request from the
	 * exact same cache the security filter chain <em>writes</em> to. Both sides therefore honor a consumer-supplied {@link RequestCache} bean (see
	 * {@code UserSecurityBeansAutoConfiguration.requestCache()}); without this, {@link SavedRequestAwareAuthenticationSuccessHandler} would fall back to
	 * its own default {@link org.springframework.security.web.savedrequest.HttpSessionRequestCache} and a consumer who overrode the cache with a
	 * different implementation (or a different session attribute) would get their saved request written to one store and read from another, silently
	 * breaking the post-login redirect to the originally-requested page.
	 * </p>
	 *
	 * @param eventPublisher the application event publisher used to emit login audit events
	 * @param requestCache the effective {@link RequestCache} bean (the library's hardened default, or a consumer override)
	 * @param userSecurityConfig the user security configuration properties
	 */
	public LoginSuccessService(ApplicationEventPublisher eventPublisher, RequestCache requestCache,
			UserSecurityConfigProperties userSecurityConfig) {
		this.eventPublisher = eventPublisher;
		this.userSecurityConfig = userSecurityConfig;
		super.setRequestCache(requestCache);
	}

	/**
	 * On authentication success.
	 *
	 * @param request the request
	 * @param response the response
	 * @param authentication the authentication
	 * @throws IOException Signals that an I/O exception has occurred.
	 * @throws ServletException the servlet exception
	 */
	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)	throws IOException,
																																	ServletException {
		log.debug("LoginSuccessService.onAuthenticationSuccess()");
		log.debug("LoginSuccessService.onAuthenticationSuccess: called with request: {}", request);
		log.debug("LoginSuccessService.onAuthenticationSuccess: called for user: {}",
				authentication != null ? authentication.getName() : null);

		// Enhanced logging to check request attributes
		log.debug("Request URI: {}", request.getRequestURI());
		log.debug("Request URL: {}", request.getRequestURL());
		log.debug("Request query string: {}", request.getQueryString());

		// Log saved request if present
		Object savedRequest = request.getSession().getAttribute("SPRING_SECURITY_SAVED_REQUEST");
		log.debug("Saved request in session: {}", savedRequest);

		log.debug("LoginSuccessService.onAuthenticationSuccess: targetUrl: {}", super.determineTargetUrl(request, response));

		stampFactorIfMissing(request, response, authentication);

		User user = null;
		if (authentication != null && authentication.getPrincipal() != null) {
			log.debug("LoginSuccessService.onAuthenticationSuccess() user: {}", authentication.getName());
			log.debug("LoginSuccessService.onAuthenticatonSuccess() authentication.getClass(): " + authentication.getClass());
			log.debug("LoginSuccessService.onAuthenticationSuccess() authentication.getPrincipal().getClass(): "
					+ authentication.getPrincipal().getClass());
			if (authentication.getPrincipal() instanceof DSUserDetails) {
				log.debug("LoginSuccessService.onAuthenticationSuccess: DSUserDetails for user: {}", authentication.getName());
				user = ((DSUserDetails) authentication.getPrincipal()).getUser();
			}
		}

		// Create audit event
		AuditEvent loginAuditEvent =
				AuditEvent.builder().source(this).user(user).sessionId(request.getSession().getId()).ipAddress(UserUtils.getClientIP(request))
						.userAgent(request.getHeader("User-Agent")).action("Login").actionStatus("Success").message("Success").build();

		// Publish audit event in a try-catch to prevent redirection issues
		try {
			eventPublisher.publishEvent(loginAuditEvent);
		} catch (Exception e) {
			log.error("Error publishing login audit event", e);
			// Continue with the login flow even if audit logging fails
		}

		// Get and set the target URL with enhanced logging
		String targetUrl = super.determineTargetUrl(request, response);
		log.debug("Initial targetUrl from super.determineTargetUrl: {}", targetUrl);

		if (StringUtils.isEmptyOrWhitespace(targetUrl) || StringUtils.equals(targetUrl, "/")) {
			targetUrl = userSecurityConfig.getLoginSuccessUri();
			log.debug("Using configured loginSuccessUri: {}", targetUrl);
			this.setDefaultTargetUrl(targetUrl);
			log.debug("LoginSuccessService.onAuthenticationSuccess: set defaultTargetUrl to: {}", this.getDefaultTargetUrl());
		} else {
			log.debug("Using existing targetUrl: {}", targetUrl);
		}

		// Set the alwaysUseDefaultTargetUrl based on configuration
		this.setAlwaysUseDefaultTargetUrl(userSecurityConfig.isAlwaysUseDefaultTargetUrl());
		log.debug("AlwaysUseDefaultTargetUrl set to: {} (configurable behavior)", this.isAlwaysUseDefaultTargetUrl());

		// Check if there's a redirect URL in the request parameters (common in OAuth2 flows)
		String continueParam = request.getParameter("continue");
		if (continueParam != null) {
			log.debug("Found 'continue' parameter in request: {}", continueParam);
		}

		// Extra logging to track redirection
		log.debug("LoginSuccessService.onAuthenticationSuccess: Proceeding with redirection to {}", this.getDefaultTargetUrl());

		// Log the SavedRequest state
		log.debug("SavedRequest state before calling super.onAuthenticationSuccess: {}",
				request.getSession().getAttribute("SPRING_SECURITY_SAVED_REQUEST"));

		super.onAuthenticationSuccess(request, response, authentication);

		// This won't execute if the super method redirects, but might help with debugging
		log.debug("After super.onAuthenticationSuccess - if you see this, no redirect happened");
	}


	/**
	 * Guarantees the session carries an authentication factor, so a freshness check has something to read.
	 *
	 * <p>
	 * {@code OidcAuthorizationCodeAuthenticationProvider} stamps no {@link FactorGrantedAuthority}, unlike the
	 * password and plain-OAuth2 providers, so an OIDC login would otherwise leave a session that can never satisfy
	 * step-up or the passkey-enrollment gate. Only an entirely unstamped authentication is touched, so the flows that
	 * already stamp are left exactly as they were.
	 * </p>
	 *
	 * <p>
	 * {@code AbstractAuthenticationProcessingFilter} saves the context before invoking this handler, so a replacement
	 * has to be written back to the repository explicitly, the same way {@code WebAuthnAuthenticationSuccessHandler}
	 * does after its principal swap.
	 * </p>
	 */
	private void stampFactorIfMissing(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
		if (authentication == null || !authentication.isAuthenticated()) {
			return;
		}
		List<GrantedAuthority> withFactor = LoginFactorStamper.ensureFactor(authentication.getAuthorities());
		if (withFactor.size() == authentication.getAuthorities().size()) {
			return;
		}

		// Only OAuth2AuthenticationToken is rebuilt: OIDC login is the unstamped flow this exists for, and it
		// produces that type. Guessing how to reconstruct an unknown token type risks losing state that matters more
		// than the factor, so anything else is left alone and logged.
		if (!(authentication instanceof OAuth2AuthenticationToken oauthToken)
				|| !(authentication.getPrincipal() instanceof OAuth2User oauth2User)) {
			log.warn("LoginSuccessService: {} carries no authentication factor and cannot be re-stamped; "
					+ "step-up and passkey enrollment will not be available to this session",
					authentication.getClass().getSimpleName());
			return;
		}
		Authentication stamped = new OAuth2AuthenticationToken(oauth2User, withFactor,
				oauthToken.getAuthorizedClientRegistrationId());
		SecurityContext context = SecurityContextHolder.getContext();
		context.setAuthentication(stamped);
		SecurityContextHolder.setContext(context);
		securityContextRepository.saveContext(context, request, response);
		log.debug("LoginSuccessService: stamped an authorization-code factor on an otherwise unstamped login for {}",
				authentication.getName());
	}

}
