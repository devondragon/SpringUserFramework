package com.digitalsanctuary.spring.user.listener;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import com.digitalsanctuary.spring.user.event.OnRegistrationCompleteEvent;
import com.digitalsanctuary.spring.user.service.UserEmailService;
import lombok.extern.slf4j.Slf4j;

/**
 * This listener handles OnRegistrationCompleteEvents, and sends a password verification email, if that feature is enabled.
 *
 * @see OnRegistrationCompleteEvent
 */
@Slf4j
@Component
public class RegistrationListener {

	@Autowired
	private UserEmailService userEmailService;

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
		if (sendRegistrationVerificationEmail) {
			this.sendRegistrationVerificationEmail(event);
		}
	}

	/**
	 * Handle the completed registration.
	 *
	 * Create a Verification token for the user, and send the email out.
	 *
	 * @param event the event
	 */
	private void sendRegistrationVerificationEmail(final OnRegistrationCompleteEvent event) {
		userEmailService.sendRegistrationVerificationEmail(event.getUser(), event.getAppUrl());
	}

}
