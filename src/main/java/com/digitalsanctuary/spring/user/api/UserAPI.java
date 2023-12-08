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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.digitalsanctuary.spring.user.dto.PasswordDto;
import com.digitalsanctuary.spring.user.dto.UserDto;
import com.digitalsanctuary.spring.user.event.AuditEvent;
import com.digitalsanctuary.spring.user.event.OnRegistrationCompleteEvent;
import com.digitalsanctuary.spring.user.exceptions.UserAlreadyExistException;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.service.DSUserDetails;
import com.digitalsanctuary.spring.user.service.UserEmailService;
import com.digitalsanctuary.spring.user.service.UserService;
import com.digitalsanctuary.spring.user.service.UserService.TokenValidationResult;
import com.digitalsanctuary.spring.user.util.JSONResponse;
import com.digitalsanctuary.spring.user.util.UserUtils;
import jakarta.servlet.ServletException;
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
	private final UserService userService;
	private final UserEmailService userEmailService;
	private final MessageSource messages;
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

	@Value("${user.actuallyDeleteAccount:false}")
	private boolean actuallyDeleteAccount;


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

			eventPublisher.publishEvent(OnRegistrationCompleteEvent.builder().user(registeredUser).locale(request.getLocale())
					.appUrl(UserUtils.getAppUrl(request)).build());

			AuditEvent registrationAuditEvent = AuditEvent.builder().source(this).user(registeredUser).sessionId(request.getSession().getId())
					.ipAddress(UserUtils.getClientIP(request)).userAgent(request.getHeader("User-Agent")).action("Registration")
					.actionStatus("Success").message("Registration Successful").build();
			eventPublisher.publishEvent(registrationAuditEvent);
		} catch (UserAlreadyExistException uaee) {
			log.warn("UserAPI.registerUserAccount:" + "UserAlreadyExistException on registration with email: {}!", userDto.getEmail());
			AuditEvent registrationAuditEvent = AuditEvent.builder().source(this).user(registeredUser).sessionId(request.getSession().getId())
					.ipAddress(UserUtils.getClientIP(request)).userAgent(request.getHeader("User-Agent")).action("Registration")
					.actionStatus("Failure").message("User Already Exists").build();

			eventPublisher.publishEvent(registrationAuditEvent);

			return new ResponseEntity<JSONResponse>(
					JSONResponse.builder().success(false).code(02).message("An account already exists for the email address").build(),
					HttpStatus.CONFLICT);
		} catch (Exception e) {
			log.error("UserAPI.registerUserAccount:" + "Exception!", e);
			AuditEvent registrationAuditEvent = AuditEvent.builder().source(this).user(registeredUser).sessionId(request.getSession().getId())
					.ipAddress(UserUtils.getClientIP(request)).userAgent(request.getHeader("User-Agent")).action("Registration")
					.actionStatus("Failure").message(e.getMessage()).build();

			eventPublisher.publishEvent(registrationAuditEvent);

			return new ResponseEntity<JSONResponse>(JSONResponse.builder().success(false).redirectUrl(null).code(05).message("System Error!").build(),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}

		// If there were no exceptions then the registration was a success!
		String nextURL = registrationPendingURI;
		if (registeredUser.isEnabled()) {
			log.debug("UserAPI.registerUserAccount:" + "User is already enabled, skipping email verification and auto-logging them in.");
			nextURL = registrationSuccessURI;
			// Auto-login the user after registration (this is a UX choice, which is why it is in the controller)
			userService.authWithoutPassword(registeredUser);
		}
		return new ResponseEntity<JSONResponse>(
				JSONResponse.builder().success(true).redirectUrl(nextURL).code(0).message("Registration Successful!").build(), HttpStatus.OK);
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
				return new ResponseEntity<JSONResponse>(JSONResponse.builder().success(false).code(1).message("Account is already verified.").build(),
						HttpStatus.CONFLICT);
			} else {
				// Else send new token email
				log.debug("UserAPI.resendRegistrationToken:" + "sending a new verification token email.");
				String appUrl = UserUtils.getAppUrl(request);
				userEmailService.sendRegistrationVerificationEmail(user, appUrl);
				// Return happy path response
				AuditEvent resendRegTokenAuditEvent = AuditEvent.builder().source(this).user(user).sessionId(request.getSession().getId())
						.ipAddress(UserUtils.getClientIP(request)).userAgent(request.getHeader("User-Agent")).action("Resend Reg Token")
						.actionStatus("Success").message("Success").build();

				eventPublisher.publishEvent(resendRegTokenAuditEvent);
				return new ResponseEntity<JSONResponse>(JSONResponse.builder().success(true).redirectUrl(registrationPendingURI).code(0)
						.message("Verification Email Resent Successfully!").build(), HttpStatus.OK);
			}
		}
		// Return generic error response (don't leak too much info)
		return new ResponseEntity<JSONResponse>(JSONResponse.builder().success(false).code(2).message("System Error!").build(),
				HttpStatus.INTERNAL_SERVER_ERROR);
	}

	@PostMapping("/updateUser")
	public ResponseEntity<JSONResponse> updateUserAccount(@AuthenticationPrincipal DSUserDetails userDetails, @Valid final UserDto userDto,
			final HttpServletRequest request, final Locale locale) {
		log.debug("UserAPI.updateUserAccount:" + "called with userDetails: {} and  userDto: {}", userDetails, userDto);
		// If the userDetails is not available, or if the user is not logged in, log an error and return a failure.
		if (userDetails == null || SecurityContextHolder.getContext().getAuthentication() == null
				|| !SecurityContextHolder.getContext().getAuthentication().isAuthenticated()) {
			log.error("UserAPI.updateUserAccount:" + "updateUser called without logged in user state!");
			return new ResponseEntity<JSONResponse>(JSONResponse.builder().success(false).message("User Not Logged In!").build(), HttpStatus.OK);
		}

		User user = userDetails.getUser();

		user.setFirstName(userDto.getFirstName());
		user.setLastName(userDto.getLastName());
		userService.saveRegisteredUser(user);

		AuditEvent userUpdateAuditEvent = AuditEvent.builder().source(this).user(userDetails.getUser()).sessionId(request.getSession().getId())
				.ipAddress(UserUtils.getClientIP(request)).userAgent(request.getHeader("User-Agent")).action("ProfileUpdate").actionStatus("Success")
				.message("Success").build();

		eventPublisher.publishEvent(userUpdateAuditEvent);


		return new ResponseEntity<JSONResponse>(
				JSONResponse.builder().success(true).message(messages.getMessage("message.updateUserSuccess", null, locale) + "<br /><br />").build(),
				HttpStatus.OK);
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
			userEmailService.sendForgotPasswordVerificationEmail(user, appUrl);

			AuditEvent resetPasswordAuditEvent =
					AuditEvent.builder().source(this).user(user).sessionId(request.getSession().getId()).ipAddress(UserUtils.getClientIP(request))
							.userAgent(request.getHeader("User-Agent")).action("Reset Password").actionStatus("Success").message("Success").build();

			eventPublisher.publishEvent(resetPasswordAuditEvent);

		} else {
			AuditEvent resetPasswordAuditEvent = AuditEvent.builder().source(this).user(user).sessionId(request.getSession().getId())
					.ipAddress(UserUtils.getClientIP(request)).userAgent(request.getHeader("User-Agent")).action("Reset Password")
					.actionStatus("Failure").message("Invalid EMail Submitted").extraData("Email submitted: " + userDto.getEmail()).build();
			eventPublisher.publishEvent(resetPasswordAuditEvent);
		}

		return new ResponseEntity<JSONResponse>(JSONResponse.builder().success(true).redirectUrl(forgotPasswordPendingURI)
				.message("If account exists, password reset email has been sent!").build(), HttpStatus.OK);
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

				AuditEvent savePasswordAuditEvent = AuditEvent.builder().source(this).user(user.get()).sessionId(request.getSession().getId())
						.ipAddress(UserUtils.getClientIP(request)).userAgent(request.getHeader("User-Agent")).action("Reset Save Password")
						.actionStatus("Success").message("Success").build();
				eventPublisher.publishEvent(savePasswordAuditEvent);

				// In this case we are returning a success, with multiple messages designed to be displayed on-page,
				// instead of a redirect URL like most of the other calls.
				return new ResponseEntity<JSONResponse>(
						JSONResponse.builder().success(true).message(messages.getMessage("message.resetPasswordSuccess", null, locale))
								.message("<a href='/user/login.html'>Login</a>").build(),
						HttpStatus.OK);
			} else {
				log.debug("UserAPI.savePassword:" + "user could not be found!");
				return new ResponseEntity<JSONResponse>(
						JSONResponse.builder().success(false).code(1).message(messages.getMessage("message.error", null, locale)).build(),
						HttpStatus.OK);
			}
		} else {
			return new ResponseEntity<JSONResponse>(
					JSONResponse.builder().success(false).code(2).message(messages.getMessage("message.error", null, locale)).build(), HttpStatus.OK);
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
			return new ResponseEntity<JSONResponse>(
					JSONResponse.builder().success(false).code(2).message(messages.getMessage("message.error", null, locale)).build(),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
		final User user = userDetails.getUser();
		// Check to see if the provided old password matches the current password
		if (!userService.checkIfValidOldPassword(user, passwordDto.getOldPassword())) {
			return new ResponseEntity<JSONResponse>(JSONResponse.builder().success(false).code(1).message("Invalid Old Password").build(),
					HttpStatus.UNAUTHORIZED);

		}
		userService.changeUserPassword(user, passwordDto.getNewPassword());

		AuditEvent updatePasswordAuditEvent =
				AuditEvent.builder().source(this).user(user).sessionId(request.getSession().getId()).ipAddress(UserUtils.getClientIP(request))
						.userAgent(request.getHeader("User-Agent")).action("Update Save Password").actionStatus("Success").message("Success").build();

		eventPublisher.publishEvent(updatePasswordAuditEvent);

		return new ResponseEntity<JSONResponse>(
				JSONResponse.builder().success(true).code(0).message(messages.getMessage("message.updatePasswordSuccess", null, locale)).build(),
				HttpStatus.OK);
	}

	/**
	 * Deletes the current user's account.
	 *
	 * @param locale the locale
	 * @param request the request
	 * @return the generic response
	 */
	@DeleteMapping("/deleteAccount")
	public ResponseEntity<JSONResponse> deleteAccount(@AuthenticationPrincipal DSUserDetails userDetails, final Locale locale,
			final HttpServletRequest request) {

		if (userDetails == null || userDetails.getUser() == null) {
			log.error("UserAPI.deleteAccount:" + "deleteAccount called with null userDetails or user.");
			return new ResponseEntity<JSONResponse>(
					JSONResponse.builder().success(false).code(2).message(messages.getMessage("message.error", null, locale)).build(),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
		final User user = userDetails.getUser();

		if (actuallyDeleteAccount) {
			userService.deleteUser(user);
		} else {
			user.setEnabled(false);
			userService.saveRegisteredUser(user);
		}
		try {
			SecurityContextHolder.clearContext();
			request.logout();
		} catch (ServletException e) {
			log.warn("UserAPI.deleteAccount:" + "Exception on logout!", e);
		}

		return new ResponseEntity<JSONResponse>(JSONResponse.builder().success(true).message("Account Deleted").build(), HttpStatus.OK);
	}


}
