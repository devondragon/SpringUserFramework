package com.digitalsanctuary.spring.user.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.digitalsanctuary.spring.user.event.OnRegistrationCompleteEvent;
import com.digitalsanctuary.spring.user.service.UserService;

/**
 * This listener handles OnRegistrationCompleteEvents, and sends a password verification email, if that feature is
 * enabled.
 *
 * @see OnRegistrationCompleteEvent
 */
@Component
public class RegistrationListener {

	/** The logger. */
	public Logger logger = LoggerFactory.getLogger(this.getClass());

	/** The user service. */
	@Autowired
	private UserService userService;

	@Value("${user.registration.sendVerificationEmail:false}")
	private boolean sendRegistrationVerificationEmail;

	/**
	 * Handles the OnRegistrationCompleteEvent. In this case calling the confirmRegistration method.
	 *
	 * @param event
	 *            the event
	 */
	@Async
	@EventListener
	public void onApplicationEvent(final OnRegistrationCompleteEvent event) {
		logger.debug("RegistrationListener.onApplicationEvent: called with event: {}", event.toString());
		if (sendRegistrationVerificationEmail) {
			this.sendRegistrationVerificationEmail(event);
		}
	}

	/**
	 * Handle the completed registration.
	 * 
	 * Create a Verification token for the user, and send the email out.
	 *
	 * @param event
	 *            the event
	 */
	private void sendRegistrationVerificationEmail(final OnRegistrationCompleteEvent event) {
		userService.sendRegistrationVerificationEmail(event.getUser(), event.getAppUrl());
	}

}
