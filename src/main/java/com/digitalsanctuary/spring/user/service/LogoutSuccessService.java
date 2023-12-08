package com.digitalsanctuary.spring.user.service;

import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler;
import org.springframework.stereotype.Service;
import org.thymeleaf.util.StringUtils;
import com.digitalsanctuary.spring.user.event.AuditEvent;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.util.UserUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The LogoutSuccessService is called when a user logs out successfully.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class LogoutSuccessService extends SimpleUrlLogoutSuccessHandler {

	/** The event publisher. */
	private final ApplicationEventPublisher eventPublisher;

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
		log.debug("LogoutSuccessService.onLogoutSuccess:" + "called.");
		log.debug("LogoutSuccessService.onAuthenticationSuccess:" + "called with authentiation: {}", authentication);
		log.debug("LogoutSuccessService.onAuthenticationSuccess:" + "targetUrl: {}", super.determineTargetUrl(request, response));

		User user = null;
		if (authentication != null && authentication.getPrincipal() != null && authentication.getPrincipal() instanceof DSUserDetails) {
			user = ((DSUserDetails) authentication.getPrincipal()).getUser();
		}

		AuditEvent logoutAuditEvent =
				AuditEvent.builder().source(this).user(user).sessionId(request.getSession().getId()).ipAddress(UserUtils.getClientIP(request))
						.userAgent(request.getHeader("User-Agent")).action("Logout").actionStatus("Success").message("Success").build();

		eventPublisher.publishEvent(logoutAuditEvent);

		String targetUrl = super.determineTargetUrl(request, response);
		if (StringUtils.isEmptyOrWhitespace(targetUrl) || StringUtils.equals(targetUrl, "/")) {
			targetUrl = logoutSuccessUri;
			this.setDefaultTargetUrl(targetUrl);
		}

		super.onLogoutSuccess(request, response, authentication);
	}
}
