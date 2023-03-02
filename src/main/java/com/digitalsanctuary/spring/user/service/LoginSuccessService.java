package com.digitalsanctuary.spring.user.service;

import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Service;
import org.thymeleaf.util.StringUtils;
import com.digitalsanctuary.spring.user.event.AuditEvent;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.util.UserUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * The LoginSuccessService is called after a user successfully logs in.
 */
@Slf4j
@Service
public class LoginSuccessService extends SavedRequestAwareAuthenticationSuccessHandler {


	/** The event publisher. */
	@Autowired
	private ApplicationEventPublisher eventPublisher;

	/** The login success uri. */
	@Value("${user.security.loginSuccessURI}")
	private String loginSuccessUri;

	@Autowired
	private OAuth2UserService oauth2UserService;

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
		System.out.println("LoginSuccessService.onAuthenticationSuccess()");
		log.debug("LoginSuccessService.onAuthenticationSuccess:" + "called with authentiation: {}", authentication);
		log.debug("LoginSuccessService.onAuthenticationSuccess:" + "targetUrl: {}", super.determineTargetUrl(request, response));

		User user = null;
		if (authentication != null && authentication.getPrincipal() != null) {
			if (authentication.getPrincipal() instanceof DSUserDetails) {
				user = ((DSUserDetails) authentication.getPrincipal()).getUser();
			} else if (authentication.getPrincipal() instanceof OAuth2User) {
				log.debug("LoginSuccessService.onAuthenticationSuccess:" + "OAuth2User: {}", authentication.getPrincipal());
				user = oauth2UserService.handleOAuthLoginSuccess("GOOGLE", (OAuth2User) authentication.getPrincipal());

			}
		}

		AuditEvent loginAuditEvent = new AuditEvent(this, user, request.getSession().getId(), UserUtils.getClientIP(request),
				request.getHeader("User-Agent"), "Login", "Success", "Success", null);
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
