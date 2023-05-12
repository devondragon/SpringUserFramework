package com.digitalsanctuary.spring.user.api;

import java.util.Locale;
import java.util.Optional;
import javax.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.digitalsanctuary.spring.user.dto.PasswordDto;
import com.digitalsanctuary.spring.user.dto.UserDto;
import com.digitalsanctuary.spring.user.event.AuditEvent;
import com.digitalsanctuary.spring.user.event.OnRegistrationCompleteEvent;
import com.digitalsanctuary.spring.user.exceptions.InvalidOldPasswordException;
import com.digitalsanctuary.spring.user.exceptions.UserAlreadyExistException;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.service.DSUserDetails;
import com.digitalsanctuary.spring.user.service.UserService;
import com.digitalsanctuary.spring.user.service.UserService.TokenValidationResult;
import com.digitalsanctuary.spring.user.util.JSONResponse;
import com.digitalsanctuary.spring.user.util.UserUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The UserAPI is the Controller for the REST API endpoints for the user management functionality. By default these endpoints are defined under the
 * "/user" prefix.
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping(path = "/user", produces = "application/json")
public class UserAPI {

	/** The user service. */
	private final UserService userService;

	/** The messages. */
	private final MessageSource messages;

	/** The event publisher. */
	private final ApplicationEventPublisher eventPublisher;

	// URIs configured in application.properties
	/** The registration pending URI. */
	@Value("${user.security.registrationPendingURI}")
	private String registrationPendingURI;

	/** The registration success URI. */
	@Value("${user.security.registrationSuccessURI}")
	private String registrationSuccessURI;

	/** The registration new verification URI. */
	@Value("${user.security.registrationNewVerificationURI}")
	private String registrationNewVerificationURI;

	/** The forgot password pending URI. */
	@Value("${user.security.forgotPasswordPendingURI}")
	private String forgotPasswordPendingURI;

	/** The forgot password change URI. */
	@Value("${user.security.forgotPasswordChangeURI}")
	private String forgotPasswordChangeURI;

	/**
	 * Register a new user account.
	 *
	 * @param userDto the userDTO object is used for passing the form data in
	 * @param request the request
	 * @return A JSONResponse. In addition to success status, message, and code in the response body, this method also returns a 200 status on
	 *         success, a 409 status if the email address is already in use, and a 502 if there is an error.
	 */
	@PostMapping("/registration")
	public ResponseEntity<JSONResponse> registerUserAccount(@Valid final UserDto userDto, final HttpServletRequest request) {
		log.debug("Registering user account with information: {}", userDto);

		User registeredUser = null;
		try {
			registeredUser = userService.registerNewUserAccount(userDto);

			eventPublisher.publishEvent(new OnRegistrationCompleteEvent(registeredUser, request.getLocale(), UserUtils.getAppUrl(request)));
			AuditEvent registrationAuditEvent = new AuditEvent(this, registeredUser, request.getSession().getId(), UserUtils.getClientIP(request),
					request.getHeader("User-Agent"), "Registration", "Success", "Registration Successful", null);
			eventPublisher.publishEvent(registrationAuditEvent);
		} catch (UserAlreadyExistException uaee) {
			log.warn("UserAPI.registerUserAccount:" + "UserAlreadyExistException on registration with email: {}!", userDto.getEmail());
			AuditEvent registrationAuditEvent = new AuditEvent(this, registeredUser, request.getSession().getId(), UserUtils.getClientIP(request),
					request.getHeader("User-Agent"), "Registration", "Failure", "User Already Exists", null);
			eventPublisher.publishEvent(registrationAuditEvent);
			return new ResponseEntity<JSONResponse>(new JSONResponse(false, null, 02, "An account already exists for that email address!"),
					HttpStatus.CONFLICT);
		} catch (Exception e) {
			log.error("UserAPI.registerUserAccount:" + "Exception!", e);
			AuditEvent registrationAuditEvent = new AuditEvent(this, registeredUser, request.getSession().getId(), UserUtils.getClientIP(request),
					request.getHeader("User-Agent"), "Registration", "Failure", e.getMessage(), null);
			eventPublisher.publishEvent(registrationAuditEvent);
			return new ResponseEntity<JSONResponse>(new JSONResponse(false, null, 05, "System Error!"), HttpStatus.INTERNAL_SERVER_ERROR);
		}
		// If there were no exceptions then the registration was a success!
		String nextURL = registrationPendingURI;
		if (registeredUser.isEnabled()) {
			log.debug("UserAPI.registerUserAccount:" + "User is already enabled, skipping email verification and auto-logging them in.");
			nextURL = registrationSuccessURI;
			// Auto-login the user after registration (this is a UX choice, which is why it is in the controller)
			userService.authWithoutPassword(registeredUser);
		}
		return new ResponseEntity<JSONResponse>(new JSONResponse(true, nextURL, 0, "Registration Successful!"), HttpStatus.OK);
	}

	/**
	 * Re-send registration verification token email.
	 *
	 * @param userDto the userDTO for passing in the email address from the form
	 * @param request the request
	 * @return the generic response
	 */
	@PostMapping("/resendRegistrationToken")
	public ResponseEntity<JSONResponse> resendRegistrationToken(final UserDto userDto, final HttpServletRequest request) {
		log.debug("UserAPI.resendRegistrationToken:" + "email: {}", userDto.getEmail());

		// Lookup User by email
		User user = userService.findUserByEmail(userDto.getEmail());
		log.debug("UserAPI.resendRegistrationToken:" + "user: {}", user);
		// If user exists
		if (user != null) {
			// If user is enabled
			if (user.isEnabled()) {
				log.debug("UserAPI.resendRegistrationToken:" + "user is already enabled.");
				// Send response with message and recommendation to login/forgot password
				return new ResponseEntity<JSONResponse>(new JSONResponse(false, null, 1, "Account is already verified."), HttpStatus.CONFLICT);
			} else {
				// Else send new token email
				log.debug("UserAPI.resendRegistrationToken:" + "sending a new verification token email.");
				String appUrl = UserUtils.getAppUrl(request);
				userService.userEmailService.sendRegistrationVerificationEmail(user, appUrl);
				// Return happy path response
				AuditEvent resendRegTokenAuditEvent = new AuditEvent(this, user, request.getSession().getId(), UserUtils.getClientIP(request),
						request.getHeader("User-Agent"), "Resend Reg Token", "Success", "Success", null);
				eventPublisher.publishEvent(resendRegTokenAuditEvent);
				return new ResponseEntity<JSONResponse>(new JSONResponse(true, registrationPendingURI, 0, "Verification Email Resent Successfully!"),
						HttpStatus.OK);
			}
		}
		// Return generic error response (don't leak too much info)
		return new ResponseEntity<JSONResponse>(new JSONResponse(false, null, 2, "System Error!"), HttpStatus.INTERNAL_SERVER_ERROR);
	}

	@PostMapping("/updateUser")
	public ResponseEntity<JSONResponse> updateUserAccount(@AuthenticationPrincipal DSUserDetails userDetails, @Valid final UserDto userDto,
			final HttpServletRequest request, final Locale locale) {
		log.debug("UserAPI.updateUserAccount:" + "called with userDetails: {} and  userDto: {}", userDetails, userDto);
		// If the userDetails is not available, or if the user is not logged in, log an error and return a failure.
		if (userDetails == null || SecurityContextHolder.getContext().getAuthentication() == null
				|| !SecurityContextHolder.getContext().getAuthentication().isAuthenticated()) {
			log.error("UserAPI.updateUserAccount:" + "updateUser called without logged in user state!");
			return new ResponseEntity<JSONResponse>(new JSONResponse(false, null, 0, "User Not Logged In!"), HttpStatus.OK);
		}

		User user = userDetails.getUser();

		user.setFirstName(userDto.getFirstName());
		user.setLastName(userDto.getLastName());
		userService.saveRegisteredUser(user);

		AuditEvent userUpdateAuditEvent = new AuditEvent(this, userDetails.getUser(), request.getSession().getId(), UserUtils.getClientIP(request),
				request.getHeader("User-Agent"), "ProfileUpdate", "Success", "Success", null);
		eventPublisher.publishEvent(userUpdateAuditEvent);

		return new ResponseEntity<JSONResponse>(
				new JSONResponse(true, null, 0, messages.getMessage("message.updateUserSuccess", null, locale) + "<br /><br />"), HttpStatus.OK);

	}

	/**
	 * Start of the forgot password flow. This API takes in an email address and, if the user exists, will send a password reset token email to them.
	 *
	 * @param userDto the userDTO for passing in the email address from the form
	 * @param request the request
	 * @return a generic success response, so as to not leak information about accounts existing or not.
	 */
	@PostMapping("/resetPassword")
	public ResponseEntity<JSONResponse> resetPassword(final UserDto userDto, final HttpServletRequest request) {
		log.debug("UserAPI.resetPassword:" + "email: {}", userDto.getEmail());

		// Lookup User by email
		User user = userService.findUserByEmail(userDto.getEmail());
		log.debug("UserAPI.resendRegistrationToken:" + "user: {}", user);

		if (user != null) {
			String appUrl = UserUtils.getAppUrl(request);
			userService.userEmailService.sendForgotPasswordVerificationEmail(user, appUrl);

			AuditEvent resetPasswordAuditEvent = new AuditEvent(this, user, request.getSession().getId(), UserUtils.getClientIP(request),
					request.getHeader("User-Agent"), "Reset Password", "Success", "Success", null);
			eventPublisher.publishEvent(resetPasswordAuditEvent);

		} else {
			AuditEvent resetPasswordAuditEvent =
					new AuditEvent(this, user, request.getSession().getId(), UserUtils.getClientIP(request), request.getHeader("User-Agent"),
							"Reset Password", "Failure", "Invalid EMail Submitted", "Email submitted: " + userDto.getEmail());
			eventPublisher.publishEvent(resetPasswordAuditEvent);
		}

		return new ResponseEntity<JSONResponse>(
				new JSONResponse(true, forgotPasswordPendingURI, 0, "If account exists, password reset email has been sent!"), HttpStatus.OK);
	}

	/**
	 * Saves a new password from a password reset flow based on a password reset token.
	 *
	 * @param locale the locale
	 * @param passwordDto the password dto
	 * @param request the request
	 * @return the generic response
	 */
	@PostMapping("/savePassword")
	public ResponseEntity<JSONResponse> savePassword(@Valid PasswordDto passwordDto, final HttpServletRequest request, final Locale locale) {
		log.debug("UserAPI.savePassword:" + "called with passwordDto: {}", passwordDto);

		final TokenValidationResult validationResult = userService.validatePasswordResetToken(passwordDto.getToken());
		log.debug("UserAPI.savePassword:" + "result: {}", validationResult);
		if (validationResult == TokenValidationResult.VALID) {
			Optional<User> user = userService.getUserByPasswordResetToken(passwordDto.getToken());
			if (user.isPresent()) {
				userService.changeUserPassword(user.get(), passwordDto.getNewPassword());
				log.debug("UserAPI.savePassword:" + "password updated!");

				AuditEvent savePasswordAuditEvent = new AuditEvent(this, user.get(), request.getSession().getId(), UserUtils.getClientIP(request),
						request.getHeader("User-Agent"), "Reset Save Password", "Success", "Success", null);
				eventPublisher.publishEvent(savePasswordAuditEvent);

				// In this case we are returning a success, with multiple messages designed to be displayed on-page,
				// instead of a redirect URL like most of the other calls. The difference is just to provide working
				// examples of each type of response handling.
				return new ResponseEntity<JSONResponse>(new JSONResponse(true, null, 0,
						messages.getMessage("message.resetPasswordSuccess", null, locale), "<br />", "<a href='/user/login.html'>Login</a>"),
						HttpStatus.OK);
			} else {
				log.debug("UserAPI.savePassword:" + "user could not be found!");
				return new ResponseEntity<JSONResponse>(new JSONResponse(false, null, 1, messages.getMessage("message.error", null, locale)),
						HttpStatus.OK);
			}
		} else {
			return new ResponseEntity<JSONResponse>(new JSONResponse(false, null, 2, messages.getMessage("message.error", null, locale)),
					HttpStatus.OK);
		}
	}


	/**
	 * Updates a user's password.
	 *
	 * @param locale the locale
	 * @param passwordDto the password dto
	 * @return the generic response
	 */
	// Change user password
	@PostMapping("/updatePassword")
	public ResponseEntity<JSONResponse> changeUserPassword(@AuthenticationPrincipal DSUserDetails userDetails, final Locale locale,
			@Valid PasswordDto passwordDto, final HttpServletRequest request) {
		if (userDetails == null || userDetails.getUser() == null) {
			log.error("UserAPI.changeUserPassword:" + "changeUserPassword called with null userDetails or user.");
			return new ResponseEntity<JSONResponse>(new JSONResponse(false, null, 2, messages.getMessage("message.error", null, locale)),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
		final User user = userDetails.getUser();
		if (!userService.checkIfValidOldPassword(user, passwordDto.getOldPassword())) {
			throw new InvalidOldPasswordException();
		}
		userService.changeUserPassword(user, passwordDto.getNewPassword());

		AuditEvent updatePasswordAuditEvent = new AuditEvent(this, user, request.getSession().getId(), UserUtils.getClientIP(request),
				request.getHeader("User-Agent"), "Update Save Password", "Success", "Success", null);
		eventPublisher.publishEvent(updatePasswordAuditEvent);

		return new ResponseEntity<JSONResponse>(
				new JSONResponse(true, null, 0, messages.getMessage("message.updatePasswordSuccess", null, locale) + "<br /><br />"), HttpStatus.OK);

	}

}
