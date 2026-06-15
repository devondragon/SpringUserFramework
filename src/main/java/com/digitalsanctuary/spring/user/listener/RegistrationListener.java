package com.digitalsanctuary.spring.user.listener;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import com.digitalsanctuary.spring.user.event.OnRegistrationCompleteEvent;
import com.digitalsanctuary.spring.user.service.UserEmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Listens for user registration events and sends verification emails when enabled.
 * This listener handles {@link OnRegistrationCompleteEvent} instances asynchronously
 * to avoid blocking the registration flow.
 *
 * <p>
 * Email verification is controlled by the {@code user.registration.sendVerificationEmail}
 * configuration property.
 * </p>
 *
 * @see OnRegistrationCompleteEvent
 * @see UserEmailService
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class RegistrationListener {

	/** The user email service. */
	private final UserEmailService userEmailService;

	@Value("${user.registration.sendVerificationEmail:false}")
	private boolean sendRegistrationVerificationEmail;

	/**
	 * Handles the OnRegistrationCompleteEvent. In this case calling the confirmRegistration method.
	 *
	 * @param event the event
	 */
	@Async
	@EventListener
	public void onApplicationEvent(final OnRegistrationCompleteEvent event) {
		log.debug("RegistrationListener.onApplicationEvent: called with event: {}", event.toString());
		// Skip sending a verification email to users who are already enabled (e.g. first-time OAuth2/OIDC
		// registrations, where the provider has already verified the email and the account is created
		// ENABLED). Form registrations that require verification are created DISABLED, so they still receive
		// the email. This lets OAuth/OIDC services publish OnRegistrationCompleteEvent so consumers can
		// observe social registrations uniformly, without sending those users a pointless verification email.
		if (event.isUserEnabled()) {
			log.debug("RegistrationListener.onApplicationEvent: user {} is already enabled; skipping verification email",
					event.getUserId());
			return;
		}
		if (sendRegistrationVerificationEmail) {
			this.sendRegistrationVerificationEmail(event);
		}
	}

	/**
	 * Handle the completed registration.
	 *
	 * Create a Verification token for the user, and send the email out.
	 *
	 * <p>
	 * The event carries only the user's id (not a live entity), so the email service reloads the {@link com.digitalsanctuary.spring.user.persistence.model.User}
	 * by id inside its own transaction. This avoids detached-entity / {@code LazyInitializationException} hazards on the
	 * async listener thread.
	 * </p>
	 *
	 * @param event the event
	 */
	private void sendRegistrationVerificationEmail(final OnRegistrationCompleteEvent event) {
		userEmailService.sendRegistrationVerificationEmail(event.getUserId(), event.getAppUrl());
	}

}
