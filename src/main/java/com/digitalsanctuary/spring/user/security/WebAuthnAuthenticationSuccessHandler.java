package com.digitalsanctuary.spring.user.security;

import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.HttpMessageConverterAuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthentication;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Authentication success handler for WebAuthn (Passkey) login that converts the {@link WebAuthnAuthentication} principal from
 * {@link org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity} to the application's {@code DSUserDetails}.
 *
 * <p>
 * Spring Security's {@code WebAuthnAuthenticationProvider} creates a {@code WebAuthnAuthentication} with {@code PublicKeyCredentialUserEntity} as the
 * principal, discarding the {@code UserDetails} it loaded. This handler restores the full {@code DSUserDetails} as the principal so the rest of the
 * application works identically regardless of login method (form login, OAuth2, or passkey).
 * </p>
 *
 * <p>
 * The handler delegates to {@link HttpMessageConverterAuthenticationSuccessHandler} to write the JSON response expected by the WebAuthn JavaScript
 * client ({@code {"authenticated": true, "redirectUrl": "..."}}).
 * </p>
 */
@Slf4j
public class WebAuthnAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

	private final UserDetailsService userDetailsService;
	private final AuthenticationSuccessHandler delegate;
	private final SecurityContextRepository securityContextRepository;
	private final SecurityContextHolderStrategy securityContextHolderStrategy;

	/**
	 * Creates a new handler with the given {@code UserDetailsService} and a default {@link HttpMessageConverterAuthenticationSuccessHandler} delegate.
	 *
	 * @param userDetailsService the service to load the full user details
	 */
	public WebAuthnAuthenticationSuccessHandler(UserDetailsService userDetailsService) {
		this(userDetailsService, new HttpMessageConverterAuthenticationSuccessHandler());
	}

	/**
	 * Creates a new handler with the given {@code UserDetailsService} and delegate handler.
	 *
	 * @param userDetailsService the service to load the full user details
	 * @param delegate the handler to delegate to after principal conversion
	 */
	public WebAuthnAuthenticationSuccessHandler(UserDetailsService userDetailsService, AuthenticationSuccessHandler delegate) {
		this.userDetailsService = userDetailsService;
		this.delegate = delegate;
		this.securityContextRepository = new HttpSessionSecurityContextRepository();
		this.securityContextHolderStrategy = SecurityContextHolder.getContextHolderStrategy();
	}

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
			throws IOException, ServletException {
		if (authentication instanceof WebAuthnAuthentication) {
			String username = authentication.getName();
			log.debug("Converting WebAuthn authentication principal to DSUserDetails for user: {}", username);

			UserDetails userDetails = userDetailsService.loadUserByUsername(username);

			// Create new authentication with DSUserDetails as principal, preserving authorities
			Authentication convertedAuth = new WebAuthnAuthenticationToken(userDetails,
					authentication.getAuthorities());

			// Update SecurityContext with the converted authentication
			SecurityContext context = securityContextHolderStrategy.getContext();
			context.setAuthentication(convertedAuth);
			securityContextHolderStrategy.setContext(context);

			// Re-save to session (AbstractAuthenticationProcessingFilter already saved the old context)
			securityContextRepository.saveContext(context, request, response);

			log.info("WebAuthn authentication principal converted to DSUserDetails for user: {}", username);
			delegate.onAuthenticationSuccess(request, response, convertedAuth);
		} else {
			delegate.onAuthenticationSuccess(request, response, authentication);
		}
	}
}
