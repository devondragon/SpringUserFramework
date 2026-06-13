package com.digitalsanctuary.spring.user.security;

import java.io.IOException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
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
 *
 * <p>
 * After converting the principal, this handler publishes an additional {@link InteractiveAuthenticationSuccessEvent} carrying the converted
 * authentication. Spring Security's {@code AbstractAuthenticationProcessingFilter} (the superclass of {@code WebAuthnAuthenticationFilter}) already
 * publishes an {@code InteractiveAuthenticationSuccessEvent} for passkey logins, but it carries the raw {@code WebAuthnAuthentication} whose principal
 * is a {@code PublicKeyCredentialUserEntity}. The framework's {@code BaseAuthenticationListener} (which loads the session-scoped user profile) ignores
 * that event because it requires a {@code DSUserDetails} principal. This handler therefore publishes a second event carrying the converted
 * {@code DSUserDetails} so that {@code BaseAuthenticationListener} fires for passkey logins exactly as it does for form and OAuth2 logins.
 * </p>
 *
 * <p>
 * Consequence: two {@code InteractiveAuthenticationSuccessEvent}s are emitted per passkey login (the framework's, with the raw principal, and this
 * handler's, with {@code DSUserDetails}). The framework's own listeners are principal-type-guarded and unaffected, but a principal-agnostic consumer
 * {@code @EventListener(InteractiveAuthenticationSuccessEvent.class)} would observe both. Note this event does <em>not</em> reset brute-force counters;
 * those are driven by {@code AuthenticationSuccessEvent} (a sibling event), not by {@code InteractiveAuthenticationSuccessEvent}.
 * </p>
 */
@Slf4j
public class WebAuthnAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

	private final UserDetailsService userDetailsService;
	private final AuthenticationSuccessHandler delegate;
	private final SecurityContextRepository securityContextRepository;
	private final SecurityContextHolderStrategy securityContextHolderStrategy;
	private final ApplicationEventPublisher eventPublisher;

	/**
	 * Creates a new handler with the given {@code UserDetailsService} and a default {@link HttpMessageConverterAuthenticationSuccessHandler} delegate.
	 *
	 * @param userDetailsService the service to load the full user details
	 */
	public WebAuthnAuthenticationSuccessHandler(UserDetailsService userDetailsService) {
		this(userDetailsService, new HttpMessageConverterAuthenticationSuccessHandler(), null);
	}

	/**
	 * Creates a new handler with the given {@code UserDetailsService} and event publisher, using a default
	 * {@link HttpMessageConverterAuthenticationSuccessHandler} delegate.
	 *
	 * @param userDetailsService the service to load the full user details
	 * @param eventPublisher the publisher used to fire an {@link InteractiveAuthenticationSuccessEvent} on successful WebAuthn login (may be null)
	 */
	public WebAuthnAuthenticationSuccessHandler(UserDetailsService userDetailsService, ApplicationEventPublisher eventPublisher) {
		this(userDetailsService, new HttpMessageConverterAuthenticationSuccessHandler(), eventPublisher);
	}

	/**
	 * Creates a new handler with the given {@code UserDetailsService} and delegate handler.
	 *
	 * @param userDetailsService the service to load the full user details
	 * @param delegate the handler to delegate to after principal conversion
	 */
	public WebAuthnAuthenticationSuccessHandler(UserDetailsService userDetailsService, AuthenticationSuccessHandler delegate) {
		this(userDetailsService, delegate, null);
	}

	/**
	 * Creates a new handler with the given {@code UserDetailsService}, delegate handler, and event publisher.
	 *
	 * @param userDetailsService the service to load the full user details
	 * @param delegate the handler to delegate to after principal conversion
	 * @param eventPublisher the publisher used to fire an {@link InteractiveAuthenticationSuccessEvent} on successful WebAuthn login (may be null)
	 */
	public WebAuthnAuthenticationSuccessHandler(UserDetailsService userDetailsService, AuthenticationSuccessHandler delegate,
			ApplicationEventPublisher eventPublisher) {
		this.userDetailsService = userDetailsService;
		this.delegate = delegate;
		this.securityContextRepository = new HttpSessionSecurityContextRepository();
		this.securityContextHolderStrategy = SecurityContextHolder.getContextHolderStrategy();
		this.eventPublisher = eventPublisher;
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

			// AbstractAuthenticationProcessingFilter (WebAuthnAuthenticationFilter's superclass) already publishes an
			// InteractiveAuthenticationSuccessEvent for this login, but it carries the raw WebAuthnAuthentication whose
			// principal is a PublicKeyCredentialUserEntity, which BaseAuthenticationListener ignores (it requires a
			// DSUserDetails principal). Publish an additional event here with the converted DSUserDetails-bearing
			// authentication so BaseAuthenticationListener loads the session profile for passkey logins, just as it does
			// for form and OAuth2 logins. This emits two InteractiveAuthenticationSuccessEvents per passkey login; the
			// framework's listeners are principal-type-guarded, but principal-agnostic consumer listeners would see both.
			// This event does not reset brute-force counters (those react to AuthenticationSuccessEvent, a sibling event).
			if (eventPublisher != null) {
				eventPublisher.publishEvent(new InteractiveAuthenticationSuccessEvent(convertedAuth, this.getClass()));
			}

			delegate.onAuthenticationSuccess(request, response, convertedAuth);
		} else {
			delegate.onAuthenticationSuccess(request, response, authentication);
		}
	}
}
