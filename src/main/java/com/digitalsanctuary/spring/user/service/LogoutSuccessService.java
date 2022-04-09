package com.digitalsanctuary.spring.user.service;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.digitalsanctuary.spring.user.event.AuditEvent;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.util.UserUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler;
import org.springframework.stereotype.Service;
import org.thymeleaf.util.StringUtils;

/**
 * The LogoutSuccessService is called when a user logs out successfully.
 */
@Service
public class LogoutSuccessService extends SimpleUrlLogoutSuccessHandler {
	/** The logger. */
	public Logger logger = LoggerFactory.getLogger(this.getClass());
	/** The event publisher. */
	@Autowired
	private ApplicationEventPublisher eventPublisher;

	/** The logout success uri. */
	@Value("${user.security.logoutSuccessURI}")
	private String logoutSuccessUri;

	/**
	 * On logout success.
	 *
	 * @param request the request
	 * @param response the response
	 * @param authentication the authentication
	 * @throws IOException Signals that an I/O exception has occurred.
	 * @throws ServletException the servlet exception
	 */
	@Override
	public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)	throws IOException,
																															ServletException {
		logger.debug("LogoutSuccessService.onLogoutSuccess:" + "called.");
		logger.debug("LogoutSuccessService.onAuthenticationSuccess:" + "called with authentiation: {}", authentication);
		logger.debug("LogoutSuccessService.onAuthenticationSuccess:" + "targetUrl: {}", super.determineTargetUrl(request, response));

		User user = null;
		if (authentication != null && authentication.getPrincipal() != null && authentication.getPrincipal() instanceof DSUserDetails) {
			user = ((DSUserDetails) authentication.getPrincipal()).getUser();
		}

		AuditEvent logoutAuditEvent = new AuditEvent(this, user, request.getSession().getId(), UserUtils.getClientIP(request),
				request.getHeader("User-Agent"), "Logout", "Success", "Success", null);
		eventPublisher.publishEvent(logoutAuditEvent);

		String targetUrl = super.determineTargetUrl(request, response);
		if (StringUtils.isEmptyOrWhitespace(targetUrl) || StringUtils.equals(targetUrl, "/")) {
			targetUrl = logoutSuccessUri;
			this.setDefaultTargetUrl(targetUrl);
		}

		super.onLogoutSuccess(request, response, authentication);
	}
}
