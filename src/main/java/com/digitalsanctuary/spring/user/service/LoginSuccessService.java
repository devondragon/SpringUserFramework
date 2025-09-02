package com.digitalsanctuary.spring.user.service;

import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Service;
import org.thymeleaf.util.StringUtils;
import com.digitalsanctuary.spring.user.audit.AuditEvent;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.util.UserUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The LoginSuccessService is called after a user successfully logs in.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class LoginSuccessService extends SavedRequestAwareAuthenticationSuccessHandler {

	/** The event publisher. */
	private final ApplicationEventPublisher eventPublisher;

	/** The login success uri. */
	@Value("${user.security.loginSuccessURI}")
	private String loginSuccessUri;

	/** Whether to always use the default target URL or respect saved requests for better UX. */
	@Value("${user.security.alwaysUseDefaultTargetUrl:false}")
	private boolean alwaysUseDefaultTargetUrl;

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
		log.debug("LoginSuccessService.onAuthenticationSuccess:" + "called with request: {}", request);
		log.debug("LoginSuccessService.onAuthenticationSuccess:" + "called with authentication: {}", authentication);

		// Enhanced logging to check request attributes
		log.debug("Request URI: {}", request.getRequestURI());
		log.debug("Request URL: {}", request.getRequestURL());
		log.debug("Request query string: {}", request.getQueryString());
		log.debug("Session ID: {}", request.getSession().getId());

		// Log saved request if present
		Object savedRequest = request.getSession().getAttribute("SPRING_SECURITY_SAVED_REQUEST");
		log.debug("Saved request in session: {}", savedRequest);

		log.debug("LoginSuccessService.onAuthenticationSuccess:" + "targetUrl: {}", super.determineTargetUrl(request, response));

		User user = null;
		if (authentication != null && authentication.getPrincipal() != null) {
			log.debug("LoginSuccessService.onAuthenticationSuccess() authentication.getPrincipal(): " + authentication.getPrincipal());
			log.debug("LoginSuccessService.onAuthenticatonSuccess() authentication.getClass(): " + authentication.getClass());
			log.debug("LoginSuccessService.onAuthenticationSuccess() authentication.getPrincipal().getClass(): "
					+ authentication.getPrincipal().getClass());
			if (authentication.getPrincipal() instanceof DSUserDetails) {
				log.debug("LoginSuccessService.onAuthenticationSuccess:" + "DSUserDetails: " + authentication.getPrincipal());
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
			targetUrl = loginSuccessUri;
			log.debug("Using configured loginSuccessUri: {}", loginSuccessUri);
			this.setDefaultTargetUrl(targetUrl);
			log.debug("LoginSuccessService.onAuthenticationSuccess:" + "set defaultTargetUrl to: {}", this.getDefaultTargetUrl());
		} else {
			log.debug("Using existing targetUrl: {}", targetUrl);
		}

		// Set the alwaysUseDefaultTargetUrl based on configuration
		this.setAlwaysUseDefaultTargetUrl(alwaysUseDefaultTargetUrl);
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

}
