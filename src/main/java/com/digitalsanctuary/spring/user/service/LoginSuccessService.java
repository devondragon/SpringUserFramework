package com.digitalsanctuary.spring.user.service;

import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
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
		log.debug("LoginSuccessService.onAuthenticationSuccess:" + "called with authentiation: {}", authentication);
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

		AuditEvent loginAuditEvent =
				AuditEvent.builder().source(this).user(user).sessionId(request.getSession().getId()).ipAddress(UserUtils.getClientIP(request))
						.userAgent(request.getHeader("User-Agent")).action("Login").actionStatus("Success").message("Success").build();

		eventPublisher.publishEvent(loginAuditEvent);

		String targetUrl = super.determineTargetUrl(request, response);
		if (StringUtils.isEmptyOrWhitespace(targetUrl) || StringUtils.equals(targetUrl, "/")) {
			targetUrl = loginSuccessUri;
			this.setDefaultTargetUrl(targetUrl);

			log.debug("LoginSuccessService.onAuthenticationSuccess:" + "set defaultTargetUrl to: {}", this.getDefaultTargetUrl());
			log.debug("LoginSuccessService.onAuthenticationSuccess:" + "defaultTargetParam: {}", this.getTargetUrlParameter());
		}

		super.onAuthenticationSuccess(request, response, authentication);
	}

}
