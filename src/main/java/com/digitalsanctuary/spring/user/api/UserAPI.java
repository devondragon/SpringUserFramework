package com.digitalsanctuary.spring.user.api;

import java.util.Locale;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.digitalsanctuary.spring.user.audit.AuditEvent;
import com.digitalsanctuary.spring.user.dto.PasswordDto;
import com.digitalsanctuary.spring.user.dto.PasswordResetRequestDto;
import com.digitalsanctuary.spring.user.dto.UserDto;
import com.digitalsanctuary.spring.user.event.OnRegistrationCompleteEvent;
import com.digitalsanctuary.spring.user.exceptions.InvalidOldPasswordException;
import com.digitalsanctuary.spring.user.exceptions.UserAlreadyExistException;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.service.DSUserDetails;
import com.digitalsanctuary.spring.user.service.UserEmailService;
import com.digitalsanctuary.spring.user.service.UserService;
import com.digitalsanctuary.spring.user.util.JSONResponse;
import com.digitalsanctuary.spring.user.util.UserUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST controller for managing user-related operations. This class handles user registration, account deletion, and other user-related endpoints.
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

	@Value("${user.security.registrationPendingURI}")
	private String registrationPendingURI;

	@Value("${user.security.registrationSuccessURI}")
	private String registrationSuccessURI;

	@Value("${user.security.forgotPasswordPendingURI}")
	private String forgotPasswordPendingURI;



	/**
	 * Registers a new user account.
	 *
	 * @param userDto the user data transfer object containing user details
	 * @param request the HTTP servlet request
	 * @return a ResponseEntity containing a JSONResponse with the registration result
	 */
	@PostMapping("/registration")
	public ResponseEntity<JSONResponse> registerUserAccount(@Valid @RequestBody UserDto userDto, HttpServletRequest request) {
		try {
			validateUserDto(userDto);
			User registeredUser = userService.registerNewUserAccount(userDto);
			publishRegistrationEvent(registeredUser, request);
			logAuditEvent("Registration", "Success", "Registration Successful", registeredUser, request);

			String nextURL = registeredUser.isEnabled() ? handleAutoLogin(registeredUser) : registrationPendingURI;

			return buildSuccessResponse("Registration Successful!", nextURL);
		} catch (UserAlreadyExistException ex) {
			log.warn("User already exists with email: {}", userDto.getEmail());
			logAuditEvent("Registration", "Failure", "User Already Exists", null, request);
			return buildErrorResponse("An account already exists for the email address", 2, HttpStatus.CONFLICT);
		} catch (Exception ex) {
			log.error("Unexpected error during registration.", ex);
			logAuditEvent("Registration", "Failure", ex.getMessage(), null, request);
			return buildErrorResponse("System Error!", 5, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Resends the registration token. This is used when the user did not receive the initial registration email.
	 *
	 * @param userDto the user data transfer object containing user details
	 * @param request the HTTP servlet request
	 * @return a ResponseEntity containing a JSONResponse with the registration result
	 */
	@PostMapping("/resendRegistrationToken")
	public ResponseEntity<JSONResponse> resendRegistrationToken(@Valid @RequestBody UserDto userDto, HttpServletRequest request) {
		User user = userService.findUserByEmail(userDto.getEmail());
		if (user != null) {
			if (user.isEnabled()) {
				return buildErrorResponse("Account is already verified.", 1, HttpStatus.CONFLICT);
			}
			userEmailService.sendRegistrationVerificationEmail(user, UserUtils.getAppUrl(request));
			logAuditEvent("Resend Reg Token", "Success", "Verification Email Resent", user, request);
			return buildSuccessResponse("Verification Email Resent Successfully!", registrationPendingURI);
		}
		return buildErrorResponse("System Error!", 2, HttpStatus.INTERNAL_SERVER_ERROR);
	}

	/**
	 * Updates the user's password. This is used when the user is logged in and wants to change their password.
	 *
	 * @param userDetails the authenticated user details
	 * @param userDto the user data transfer object containing user details
	 * @param request the HTTP servlet request
	 * @param locale the locale
	 * @return a ResponseEntity containing a JSONResponse with the password update result
	 */
	@PostMapping("/updateUser")
	public ResponseEntity<JSONResponse> updateUserAccount(@AuthenticationPrincipal DSUserDetails userDetails, @Valid @RequestBody UserDto userDto,
			HttpServletRequest request, Locale locale) {
		validateAuthenticatedUser(userDetails);
		User user = userDetails.getUser();
		user.setFirstName(userDto.getFirstName());
		user.setLastName(userDto.getLastName());
		userService.saveRegisteredUser(user);

		logAuditEvent("ProfileUpdate", "Success", "User profile updated", user, request);

		return buildSuccessResponse(messages.getMessage("message.update-user.success", null, locale), null);
	}

	/**
	 * This is used when the user has forgotten their password and wants to reset their password. This will send an email to the user with a link to
	 * reset their password.
	 *
	 * @param passwordResetRequest the password reset request containing the email address
	 * @param request the HTTP servlet request
	 * @return a ResponseEntity containing a JSONResponse with the password reset email send result
	 */
	@PostMapping("/resetPassword")
	public ResponseEntity<JSONResponse> resetPassword(@Valid @RequestBody PasswordResetRequestDto passwordResetRequest, HttpServletRequest request) {
		User user = userService.findUserByEmail(passwordResetRequest.getEmail());
		if (user != null) {
			userEmailService.sendForgotPasswordVerificationEmail(user, UserUtils.getAppUrl(request));
			logAuditEvent("Reset Password", "Success", "Password reset email sent", user, request);
		}
		return buildSuccessResponse("If account exists, password reset email has been sent!", forgotPasswordPendingURI);
	}

	/**
	 * Updates the user's password. This is used when the user is logged in and wants to change their password.
	 *
	 * @param userDetails the authenticated user details
	 * @param passwordDto the password data transfer object containing the old and new passwords
	 * @param request the HTTP servlet request
	 * @param locale the locale
	 * @return a ResponseEntity containing a JSONResponse with the password update result
	 */
	@PostMapping("/updatePassword")
	public ResponseEntity<JSONResponse> updatePassword(@AuthenticationPrincipal DSUserDetails userDetails,
			@Valid @RequestBody PasswordDto passwordDto, HttpServletRequest request, Locale locale) {
		validateAuthenticatedUser(userDetails);
		User user = userDetails.getUser();

		try {
			if (!userService.checkIfValidOldPassword(user, passwordDto.getOldPassword())) {
				throw new InvalidOldPasswordException("Invalid old password");
			}

			userService.changeUserPassword(user, passwordDto.getNewPassword());
			logAuditEvent("PasswordUpdate", "Success", "User password updated", user, request);

			return buildSuccessResponse(messages.getMessage("message.update-password.success", null, locale), null);
		} catch (InvalidOldPasswordException ex) {
			logAuditEvent("PasswordUpdate", "Failure", "Invalid old password", user, request);
			return buildErrorResponse(messages.getMessage("message.update-password.invalid-old", null, locale), 1, HttpStatus.BAD_REQUEST);
		} catch (Exception ex) {
			log.error("Unexpected error during password update.", ex);
			logAuditEvent("PasswordUpdate", "Failure", ex.getMessage(), user, request);
			return buildErrorResponse("System Error!", 5, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Deletes the user's account. This is used when the user wants to delete their account. This will either delete the account or disable it based
	 * on the configuration of the actuallyDeleteAccount property. After the account is disabled or deleted, the user will be logged out.
	 *
	 * @param userDetails the authenticated user details
	 * @param request the HTTP servlet request
	 * @return a ResponseEntity containing a JSONResponse with the account deletion result
	 */
	@DeleteMapping("/deleteAccount")
	public ResponseEntity<JSONResponse> deleteAccount(@AuthenticationPrincipal DSUserDetails userDetails, HttpServletRequest request) {
		validateAuthenticatedUser(userDetails);
		User user = userDetails.getUser();
		userService.deleteOrDisableUser(user);
		logAuditEvent("AccountDelete", "Success", "User account deleted", user, request);
		logoutUser(request);
		return buildSuccessResponse("Account Deleted", null);
	}

	// Helper Methods
	/**
	 * Validates the user data transfer object.
	 *
	 * @param userDto the user data transfer object
	 */
	private void validateUserDto(UserDto userDto) {
		if (isNullOrEmpty(userDto.getEmail())) {
			throw new IllegalArgumentException("Email is required.");
		}
		if (isNullOrEmpty(userDto.getPassword())) {
			throw new IllegalArgumentException("Password is required.");
		}
	}

	/**
	 * Validates the authenticated user.
	 *
	 * @param userDetails the authenticated user details
	 */
	private void validateAuthenticatedUser(DSUserDetails userDetails) {
		if (userDetails == null || userDetails.getUser() == null) {
			throw new SecurityException("User not logged in.");
		}
	}

	/**
	 * Handles the auto login of the user after registration.
	 *
	 * @param user the registered user
	 * @return the URI to redirect to after registration
	 */
	private String handleAutoLogin(User user) {
		userService.authWithoutPassword(user);
		return registrationSuccessURI;
	}

	/**
	 * Logs out the user.
	 *
	 * @param request the HTTP servlet request
	 */
	private void logoutUser(HttpServletRequest request) {
		try {
			SecurityContextHolder.clearContext();
			request.logout();
		} catch (ServletException e) {
			log.warn("Logout failed during account deletion.", e);
		}
	}

	/**
	 * Publishes a registration event.
	 *
	 * @param user the registered user
	 * @param request the HTTP servlet request
	 */
	private void publishRegistrationEvent(User user, HttpServletRequest request) {
		String appUrl = UserUtils.getAppUrl(request);
		eventPublisher.publishEvent(new OnRegistrationCompleteEvent(user, request.getLocale(), appUrl));
	}

	/**
	 * Logs an audit event.
	 *
	 * @param action the action performed
	 * @param status the status of the action
	 * @param message the message describing the action
	 * @param user the user involved in the action
	 * @param request the HTTP servlet request
	 */
	private void logAuditEvent(String action, String status, String message, User user, HttpServletRequest request) {
		AuditEvent event =
				AuditEvent.builder().source(this).user(user).sessionId(request.getSession().getId()).ipAddress(UserUtils.getClientIP(request))
						.userAgent(request.getHeader("User-Agent")).action(action).actionStatus(status).message(message).build();
		eventPublisher.publishEvent(event);
	}

	/**
	 * Checks if a string is null or empty.
	 *
	 * @param value
	 * @return true if the string is null or empty, false otherwise
	 */
	private boolean isNullOrEmpty(String value) {
		return value == null || value.isEmpty();
	}

	/**
	 * Builds an error response.
	 *
	 * @param message
	 * @param code
	 * @param status
	 * @return a ResponseEntity containing a JSONResponse with the error response
	 */
	private ResponseEntity<JSONResponse> buildErrorResponse(String message, int code, HttpStatus status) {
		return ResponseEntity.status(status).body(JSONResponse.builder().success(false).code(code).message(message).build());
	}

	/**
	 * Builds a success response.
	 *
	 * @param message
	 * @param redirectUrl
	 * @return a ResponseEntity containing a JSONResponse with the success response
	 */
	private ResponseEntity<JSONResponse> buildSuccessResponse(String message, String redirectUrl) {
		return ResponseEntity.ok(JSONResponse.builder().success(true).code(0).message(message).redirectUrl(redirectUrl).build());
	}
}
