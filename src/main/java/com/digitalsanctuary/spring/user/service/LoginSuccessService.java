package com.digitalsanctuary.spring.user.service;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.thymeleaf.util.StringUtils;
import com.digitalsanctuary.spring.user.event.AuditEvent;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.util.UserUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * The LoginSuccessService is called after a user successfully logs in.
 */
@Component
public class LoginSuccessService extends SavedRequestAwareAuthenticationSuccessHandler {

	/** The logger. */
	public Logger logger = LoggerFactory.getLogger(this.getClass());
	/** The event publisher. */
	@Autowired
	private ApplicationEventPublisher eventPublisher;

	/** The login success uri. */
	@Value("${user.security.loginSuccessURI}")
	private String loginSuccessUri;

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
		logger.debug("LoginSuccessService.onAuthenticationSuccess:" + "called with authentiation: {}", authentication);
		logger.debug("LoginSuccessService.onAuthenticationSuccess:" + "targetUrl: {}", super.determineTargetUrl(request, response));

		User user = null;
		if (authentication != null && authentication.getPrincipal() != null && authentication.getPrincipal() instanceof DSUserDetails) {
			user = ((DSUserDetails) authentication.getPrincipal()).getUser();
		}

		AuditEvent loginAuditEvent = new AuditEvent(this, user, request.getSession().getId(), UserUtils.getClientIP(request),
				request.getHeader("User-Agent"), "Login", "Success", "Success", null);
		eventPublisher.publishEvent(loginAuditEvent);

		String targetUrl = super.determineTargetUrl(request, response);
		if (StringUtils.isEmptyOrWhitespace(targetUrl) || StringUtils.equals(targetUrl, "/")) {
			targetUrl = loginSuccessUri;
			this.setDefaultTargetUrl(targetUrl);

			logger.debug("LoginSuccessService.onAuthenticationSuccess:" + "set defaultTargetUrl to: {}", this.getDefaultTargetUrl());
			logger.debug("LoginSuccessService.onAuthenticationSuccess:" + "defaultTargetParam: {}", this.getTargetUrlParameter());
		}

		super.onAuthenticationSuccess(request, response, authentication);
	}

}
