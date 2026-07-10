package com.digitalsanctuary.spring.user.api;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.digitalsanctuary.spring.user.audit.AuditEvent;
import com.digitalsanctuary.spring.user.dto.AuthMethodsResponse;
import com.digitalsanctuary.spring.user.dto.PasswordDto;
import com.digitalsanctuary.spring.user.dto.PasswordResetRequestDto;
import com.digitalsanctuary.spring.user.dto.PasswordlessRegistrationDto;
import com.digitalsanctuary.spring.user.dto.SavePasswordDto;
import com.digitalsanctuary.spring.user.dto.SetPasswordDto;
import com.digitalsanctuary.spring.user.dto.UserDto;
import com.digitalsanctuary.spring.user.dto.UserProfileUpdateDto;
import com.digitalsanctuary.spring.user.event.OnRegistrationCompleteEvent;
import com.digitalsanctuary.spring.user.exceptions.InvalidOldPasswordException;
import com.digitalsanctuary.spring.user.exceptions.UserAlreadyExistException;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.registration.RegistrationDeniedException;
import com.digitalsanctuary.spring.user.registration.RegistrationGuard;
import com.digitalsanctuary.spring.user.security.StepUpService;
import com.digitalsanctuary.spring.user.service.DSUserDetails;
import com.digitalsanctuary.spring.user.service.LoginAttemptService;
import com.digitalsanctuary.spring.user.service.PasswordPolicyService;
import com.digitalsanctuary.spring.user.service.UserEmailService;
import com.digitalsanctuary.spring.user.service.UserService;
import com.digitalsanctuary.spring.user.service.WebAuthnCredentialManagementService;
import com.digitalsanctuary.spring.user.util.AppUrlResolver;
import com.digitalsanctuary.spring.user.util.JSONResponse;
import com.digitalsanctuary.spring.user.util.UserUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST API controller for user management operations.
 * <p>
 * Provides JSON endpoints for user registration, authentication, profile updates,
 * password management, and account deletion. All endpoints are mapped under
 * {@code /user} and return JSON responses.
 * </p>
 *
 * @author Devon Hillard
 * @see UserService
 * @see UserEmailService
 */
@Slf4j
@RequiredArgsConstructor
@RestController("dsUserAPI")
@RequestMapping(path = "/user", produces = "application/json")
public class UserAPI {

	/** Error code returned when the {@link RegistrationGuard} denies a registration attempt. */
	private static final int ERROR_CODE_REGISTRATION_DENIED = 6;

	/**
	 * Generic, success-shaped message returned by {@code /registration} for every outcome (new account
	 * created, or email already registered). Keeping the body identical across cases prevents an attacker
	 * from using the registration endpoint to enumerate which email addresses are already registered.
	 */
	private static final String REGISTRATION_GENERIC_MESSAGE =
			"If your email address is eligible, you will receive a verification email shortly.";

	/**
	 * Generic, success-shaped message returned by {@code /resendRegistrationToken} for every outcome
	 * (email unknown, account already verified, or verification email actually resent). Keeping the body
	 * identical across cases prevents enumeration of which emails exist and which are already verified.
	 */
	private static final String RESEND_GENERIC_MESSAGE =
			"If your account requires verification, a new verification email has been sent.";

	private final UserService userService;
	private final UserEmailService userEmailService;
	private final MessageSource messages;
	private final ApplicationEventPublisher eventPublisher;
	private final PasswordPolicyService passwordPolicyService;
	private final ObjectProvider<WebAuthnCredentialManagementService> webAuthnCredentialManagementServiceProvider;
	private final AppUrlResolver appUrlResolver;
	private final LoginAttemptService loginAttemptService;
	/** Optional consumer-provided step-up (re-)authentication service; see {@link StepUpService} (SUF-02). */
	private final ObjectProvider<StepUpService> stepUpServiceProvider;

	@Value("${user.security.registrationPendingURI}")
	private String registrationPendingURI;

	@Value("${user.security.registrationSuccessURI}")
	private String registrationSuccessURI;

	@Value("${user.security.forgotPasswordPendingURI}")
	private String forgotPasswordPendingURI;

	/**
	 * SUF-02: controls the fallback behavior of {@code /user/setPassword} when no {@link StepUpService} bean is present.
	 * When {@code false} (the default), setting an initial password on a passwordless account is disabled unless a
	 * {@link StepUpService} is provided; set to {@code true} to explicitly allow the session-only behavior.
	 */
	@Value("${user.security.allowInitialPasswordSetWithoutStepUp:false}")
	private boolean allowInitialPasswordSetWithoutStepUp;

	/**
	 * Registers a new user account.
	 *
	 * @param userDto the user data transfer object containing user details
	 * @param request the HTTP servlet request
	 * @return a ResponseEntity containing a JSONResponse with the registration
	 *         result
	 */
	@PostMapping("/registration")
	public ResponseEntity<JSONResponse> registerUserAccount(@Valid @RequestBody UserDto userDto,
			HttpServletRequest request) {
		try {
			validateUserDto(userDto);

			// Password Policy Enforcement
			// Note: Passing null for user during registration means password history
			// is not checked (new users have no history). This is intentional - only
			// existing users are checked against their own password history.
			List<String> errors = passwordPolicyService.validate(null, userDto.getPassword(),
					userDto.getEmail(), request.getLocale());

			// Check if any password validation errors exist
			if (!errors.isEmpty()) {
				log.warn("Password validation failed: {}", errors);
				return buildErrorResponse(String.join(" ", errors), 1, HttpStatus.BAD_REQUEST);
			}

			// The RegistrationGuard is now enforced inside UserService.registerNewUserAccount so that every
			// registration path is guarded exactly once and direct service callers cannot bypass it. A
			// denial surfaces as RegistrationDeniedException, translated below into the same
			// REGISTRATION_DENIED response this endpoint returned previously.
			User registeredUser = userService.registerNewUserAccount(userDto);
			publishRegistrationEvent(registeredUser, request);
			logAuditEvent("Registration", "Success", "Registration Successful", registeredUser, request);

			String nextURL = registeredUser.isEnabled() ? handleAutoLogin(registeredUser) : registrationPendingURI;

			return buildSuccessResponse(REGISTRATION_GENERIC_MESSAGE, nextURL);
		} catch (RegistrationDeniedException ex) {
			log.info("Registration denied for email: {} source: FORM reason: {}", userDto.getEmail(), ex.getReason());
			logAuditEvent("Registration", "Failure", "Registration Denied: " + ex.getReason(), null, request);
			return buildErrorResponse(ex.getReason(), ERROR_CODE_REGISTRATION_DENIED, HttpStatus.FORBIDDEN);
		} catch (UserAlreadyExistException ex) {
			// Anti-enumeration: the email is already registered, so we create NOTHING and publish no
			// registration event, but we return exactly the same generic 200 response a brand-new
			// registration would produce. The true reason is recorded server-side via the audit event.
			//
			// Returning registrationPendingURI here mirrors what a genuine new (unverified) registration
			// returns in the default verification-enabled config, making both cases indistinguishable to
			// the caller. In verification-disabled / auto-login mode a real new registration additionally
			// establishes a session — that is an inherent, accepted difference that cannot be avoided
			// without skipping auto-login for legitimate new users.
			log.warn("User already exists with email: {}", userDto.getEmail());
			logAuditEvent("Registration", "Failure", "User Already Exists", null, request);
			return buildSuccessResponse(REGISTRATION_GENERIC_MESSAGE, registrationPendingURI);
		} catch (Exception ex) {
			log.error("Unexpected error during registration.", ex);
			logAuditEvent("Registration", "Failure", ex.getMessage(), null, request);
			return buildErrorResponse("System Error!", 5, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Resends the registration token. This is used when the user did not receive
	 * the initial registration email.
	 *
	 * @param userDto the user data transfer object containing user details
	 * @param request the HTTP servlet request
	 * @return a ResponseEntity containing a JSONResponse with the registration
	 *         result
	 */
	@PostMapping("/resendRegistrationToken")
	public ResponseEntity<JSONResponse> resendRegistrationToken(@Valid @RequestBody UserDto userDto,
			HttpServletRequest request) {
		// Anti-enumeration: this endpoint ALWAYS returns the same generic 200 response, regardless of
		// whether the email is unknown, already verified, or genuinely awaiting verification. Internally
		// we only send the verification email when the account exists AND is still unverified. The true
		// outcome is recorded server-side via audit/log events so operators retain visibility.
		User user = userService.findUserByEmail(userDto.getEmail());
		if (user == null) {
			log.info("Resend verification requested for unknown email; returning generic response.");
			logAuditEvent("Resend Reg Token", "Failure", "Unknown Email", null, request);
		} else if (user.isEnabled()) {
			log.info("Resend verification requested for already-verified account; returning generic response.");
			logAuditEvent("Resend Reg Token", "Failure", "Account Already Verified", user, request);
		} else {
			userEmailService.sendRegistrationVerificationEmail(user, appUrlResolver.resolveAppUrl(request));
			logAuditEvent("Resend Reg Token", "Success", "Verification Email Resent", user, request);
		}
		return buildSuccessResponse(RESEND_GENERIC_MESSAGE, registrationPendingURI);
	}

	/**
	 * Updates the user's profile (first name, last name). This is used when the
	 * user is logged in and wants to update their profile information.
	 *
	 * @param userDetails      the authenticated user details
	 * @param profileUpdateDto the profile update DTO containing first and last name
	 * @param request          the HTTP servlet request
	 * @param locale           the locale
	 * @return a ResponseEntity containing a JSONResponse with the profile update
	 *         result
	 */
	@PostMapping("/updateUser")
	public ResponseEntity<JSONResponse> updateUserAccount(@AuthenticationPrincipal DSUserDetails userDetails,
			@Valid @RequestBody UserProfileUpdateDto profileUpdateDto,
			HttpServletRequest request, Locale locale) {
		validateAuthenticatedUser(userDetails);
		// Re-fetch user from database to ensure we have an attached entity
		User user = userService.findUserByEmail(userDetails.getUser().getEmail());
		if (user == null) {
			log.error("User not found in database: {}", userDetails.getUser().getEmail());
			return buildErrorResponse(messages.getMessage("message.user.not-found", null, "User not found", locale), 1, HttpStatus.BAD_REQUEST);
		}
		user.setFirstName(profileUpdateDto.getFirstName());
		user.setLastName(profileUpdateDto.getLastName());
		userService.saveRegisteredUser(user);

		logAuditEvent("ProfileUpdate", "Success", "User profile updated", user, request);

		return buildSuccessResponse(messages.getMessage("message.update-user.success", null, "Profile updated successfully", locale), null);
	}

	/**
	 * This is used when the user has forgotten their password and wants to reset
	 * their password. This will send an email to the user with a link to
	 * reset their password.
	 *
	 * @param passwordResetRequest the password reset request containing the email address
	 * @param request the HTTP servlet request
	 * @return a ResponseEntity containing a JSONResponse with the password reset
	 *         email send result
	 */
	@PostMapping("/resetPassword")
	public ResponseEntity<JSONResponse> resetPassword(@Valid @RequestBody PasswordResetRequestDto passwordResetRequest, HttpServletRequest request) {
		User user = userService.findUserByEmail(passwordResetRequest.getEmail());
		if (user != null) {
			userEmailService.sendForgotPasswordVerificationEmail(user, appUrlResolver.resolveAppUrl(request));
			logAuditEvent("Reset Password", "Success", "Password reset email sent", user, request);
		}
		return buildSuccessResponse("If account exists, password reset email has been sent!", forgotPasswordPendingURI);
	}

	/**
	 * Saves a new password after password reset token validation.
	 * This endpoint is called from the password reset form after the user
	 * clicks the link in their email and enters a new password.
	 *
	 * @param savePasswordDto DTO containing token and new password
	 * @param request         HTTP servlet request
	 * @param locale          locale for messages
	 * @return ResponseEntity with success or error response
	 */
	@PostMapping("/savePassword")
	public ResponseEntity<JSONResponse> savePassword(@Valid @RequestBody SavePasswordDto savePasswordDto,
			HttpServletRequest request, Locale locale) {

		try {
			// Validate passwords match
			// Note: Using equals() is safe here - we're comparing two user-provided strings
			// from the same request (not comparing against a stored secret), so timing attacks
			// are not a concern. Constant-time comparison is only needed when comparing
			// against stored credentials, which is handled by Spring's PasswordEncoder.
			if (!savePasswordDto.getNewPassword().equals(savePasswordDto.getConfirmPassword())) {
				return buildErrorResponse(messages.getMessage("message.password.mismatch", null, "Passwords do not match", locale), 1,
						HttpStatus.BAD_REQUEST);
			}

			// Validate the reset token
			UserService.TokenValidationResult tokenResult = userService
					.validatePasswordResetToken(savePasswordDto.getToken());

			if (tokenResult != UserService.TokenValidationResult.VALID) {
				String messageKey = "auth.message." + tokenResult.getValue();
				return buildErrorResponse(messages.getMessage(messageKey, null, "Invalid or expired token", locale), 2, HttpStatus.BAD_REQUEST);
			}

			// Get user by token
			Optional<User> userOptional = userService.getUserByPasswordResetToken(savePasswordDto.getToken());

			if (userOptional.isEmpty()) {
				return buildErrorResponse(messages.getMessage("auth.message.invalid", null, "Invalid token", locale), 3,
						HttpStatus.BAD_REQUEST);
			}

			User user = userOptional.get();

			// Validate new password against policy
			List<String> errors = passwordPolicyService.validate(user, savePasswordDto.getNewPassword(),
					user.getEmail(), locale);

			if (!errors.isEmpty()) {
				log.warn("Password validation failed during reset for user {}: {}", user.getEmail(), errors);
				return buildErrorResponse(String.join(" ", errors), 4, HttpStatus.BAD_REQUEST);
			}

			// Atomically consume the reset token: this validates the token is still present and
			// deletes it in a single transaction so it cannot be double-consumed by a concurrent
			// request. If it returns null, the token was already used or expired between validation
			// above and now.
			User consumedUser = userService.validateAndConsumePasswordResetToken(savePasswordDto.getToken());
			if (consumedUser == null) {
				return buildErrorResponse(messages.getMessage("auth.message.invalid", null, "Invalid token", locale), 3,
						HttpStatus.BAD_REQUEST);
			}

			// Save the new password (this also saves to history)
			userService.changeUserPassword(consumedUser, savePasswordDto.getNewPassword());

			logAuditEvent("PasswordReset", "Success", "Password reset completed", consumedUser, request);

			return buildSuccessResponse(messages.getMessage("message.reset-password.success", null, "Password has been reset successfully", locale),
					"/user/login.html");

		} catch (Exception ex) {
			log.error("Unexpected error during password reset.", ex);
			logAuditEvent("PasswordReset", "Failure", ex.getMessage(), null, request);
			return buildErrorResponse("System Error!", 5, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Updates the user's password. This is used when the user is logged in and
	 * wants to change their password.
	 *
	 * @param userDetails the authenticated user details
	 * @param passwordDto the password data transfer object containing the old and
	 *                    new passwords
	 * @param request     the HTTP servlet request
	 * @param locale      the locale
	 * @return a ResponseEntity containing a JSONResponse with the password update
	 *         result
	 */
	@PostMapping("/updatePassword")
	public ResponseEntity<JSONResponse> updatePassword(@AuthenticationPrincipal DSUserDetails userDetails,
			@Valid @RequestBody PasswordDto passwordDto, HttpServletRequest request, Locale locale) {
		validateAuthenticatedUser(userDetails);
		// Re-fetch user from database to ensure we have an attached entity
		User user = userService.findUserByEmail(userDetails.getUser().getEmail());
		if (user == null) {
			log.error("User not found in database: {}", userDetails.getUser().getEmail());
			return buildErrorResponse(messages.getMessage("message.user.not-found", null, "User not found", locale), 1, HttpStatus.BAD_REQUEST);
		}

		// A passwordless (passkey-only / OAuth-only) account has no current password to verify or change here.
		// checkIfValidOldPassword() always returns false for such an account, so without this guard every call would
		// report a "failed attempt" to the lockout counter below — letting any authenticated (or session-hijacking)
		// caller lock the account out of EVERY authentication method by hitting this endpoint repeatedly. Reject up
		// front, before the lockout logic and without touching the counter, and point the user at the set-password
		// flow. Mirrors WebAuthnManagementAPI.requireCurrentPasswordIfSet and the symmetric guard in setPassword().
		if (!userService.hasPassword(user)) {
			logAuditEvent("PasswordUpdate", "Failure", "No password set", user, request);
			return buildErrorResponse(messages.getMessage("message.update-password.no-password", null,
					"No password is set on this account. Use the set password feature instead.", locale), 4, HttpStatus.BAD_REQUEST);
		}

		// Verifying the current password is an authentication surface, so it participates in the same brute-force
		// lockout as login: reject a locked account up front (HTTP 423) so a session-holding actor cannot make
		// unlimited old-password guesses here. Mirrors WebAuthnManagementAPI.requireCurrentPasswordIfSet.
		if (loginAttemptService.isLocked(user.getEmail())) {
			logAuditEvent("PasswordUpdate", "Failure", "Account locked", user, request);
			return buildErrorResponse(messages.getMessage("message.update-password.account-locked", null,
					"Account is locked due to too many failed attempts. Please try again later.", locale), 3, HttpStatus.LOCKED);
		}

		try {
			// Verify old password is correct
			if (!userService.checkIfValidOldPassword(user, passwordDto.getOldPassword())) {
				// A wrong guess counts toward lockout, locking the account once the configured threshold is reached.
				loginAttemptService.loginFailed(user.getEmail());
				throw new InvalidOldPasswordException("Invalid old password");
			}
			// Successful reauthentication clears the failed-attempt counter, matching login semantics.
			loginAttemptService.loginSucceeded(user.getEmail());

			// Validate new password against policy
			List<String> errors = passwordPolicyService.validate(user, passwordDto.getNewPassword(), user.getEmail(),
					locale);

			if (!errors.isEmpty()) {
				log.warn("Password validation failed for user {}: {}", user.getEmail(), errors);
				return buildErrorResponse(String.join(" ", errors), 2, HttpStatus.BAD_REQUEST);
			}

			// Save the new password (this also saves to history)
			userService.changeUserPassword(user, passwordDto.getNewPassword());
			logAuditEvent("PasswordUpdate", "Success", "User password updated", user, request);

			return buildSuccessResponse(messages.getMessage("message.update-password.success", null, "Password updated successfully", locale), null);
		} catch (InvalidOldPasswordException ex) {
			logAuditEvent("PasswordUpdate", "Failure", "Invalid old password", user, request);
			return buildErrorResponse(messages.getMessage("message.update-password.invalid-old", null, "Invalid old password", locale), 1,
					HttpStatus.BAD_REQUEST);
		} catch (Exception ex) {
			log.error("Unexpected error during password update.", ex);
			logAuditEvent("PasswordUpdate", "Failure", ex.getMessage(), user, request);
			return buildErrorResponse("System Error!", 5, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Deletes the user's account. This is used when the user wants to delete their
	 * account. This will either delete the account or disable it based
	 * on the configuration of the actuallyDeleteAccount property. After the account
	 * is disabled or deleted, the user will be logged out.
	 *
	 * @param userDetails the authenticated user details
	 * @param request     the HTTP servlet request
	 * @return a ResponseEntity containing a JSONResponse with the account deletion
	 *         result
	 */
	@DeleteMapping("/deleteAccount")
	public ResponseEntity<JSONResponse> deleteAccount(@AuthenticationPrincipal DSUserDetails userDetails,
			HttpServletRequest request) {
		validateAuthenticatedUser(userDetails);
		User user = userDetails.getUser();
		userService.deleteOrDisableUser(user);
		logAuditEvent("AccountDelete", "Success", "User account deleted", user, request);
		logoutUser(request);
		return buildSuccessResponse("Account Deleted", null);
	}

	/**
	 * Returns the authentication methods configured for the current user.
	 *
	 * @param userDetails the authenticated user details
	 * @return a ResponseEntity containing the auth methods response
	 */
	@GetMapping("/auth-methods")
	public ResponseEntity<JSONResponse> getAuthMethods(@AuthenticationPrincipal DSUserDetails userDetails) {
		validateAuthenticatedUser(userDetails);
		User user = userService.findUserByEmail(userDetails.getUser().getEmail());
		if (user == null) {
			return buildErrorResponse("User not found", 1, HttpStatus.BAD_REQUEST);
		}

		WebAuthnCredentialManagementService webAuthnService = webAuthnCredentialManagementServiceProvider.getIfAvailable();
		boolean hasPasskeys = false;
		long passkeysCount = 0;
		if (webAuthnService != null) {
			passkeysCount = webAuthnService.getCredentialCount(user);
			hasPasskeys = passkeysCount > 0;
		}

		AuthMethodsResponse authMethods = AuthMethodsResponse.builder()
				.hasPassword(userService.hasPassword(user))
				.hasPasskeys(hasPasskeys)
				.passkeysCount(passkeysCount)
				.webAuthnEnabled(webAuthnService != null)
				.provider(user.getProvider())
				.build();

		return ResponseEntity.ok(JSONResponse.builder().success(true).data(authMethods).build());
	}

	/**
	 * Registers a new passwordless user account (passkey-only).
	 *
	 * <p><strong>Note:</strong> Consuming applications using {@code user.security.defaultAction: deny}
	 * must add {@code /user/registration/passwordless} to their {@code user.security.unprotectedURIs}
	 * configuration to allow unauthenticated access to this endpoint.
	 *
	 * @param dto the passwordless registration DTO
	 * @param request the HTTP servlet request
	 * @return a ResponseEntity containing a JSONResponse with the registration result
	 */
	@PostMapping("/registration/passwordless")
	public ResponseEntity<JSONResponse> registerPasswordlessAccount(@Valid @RequestBody PasswordlessRegistrationDto dto,
			HttpServletRequest request) {
		if (webAuthnCredentialManagementServiceProvider.getIfAvailable() == null) {
			return buildErrorResponse("Passwordless registration is not available", 1, HttpStatus.BAD_REQUEST);
		}
		try {
			// The RegistrationGuard is now enforced inside UserService.registerPasswordlessAccount so that
			// every registration path is guarded exactly once and direct service callers cannot bypass it.
			// A denial surfaces as RegistrationDeniedException, translated below into the same
			// REGISTRATION_DENIED response this endpoint returned previously.
			User registeredUser = userService.registerPasswordlessAccount(dto);
			publishRegistrationEvent(registeredUser, request);
			logAuditEvent("PasswordlessRegistration", "Success", "Passwordless registration successful", registeredUser, request);

			String nextURL = registeredUser.isEnabled() ? handleAutoLogin(registeredUser) : registrationPendingURI;

			return buildSuccessResponse("Registration Successful!", nextURL);
		} catch (RegistrationDeniedException ex) {
			log.info("Registration denied for email: {} source: PASSWORDLESS reason: {}", dto.getEmail(), ex.getReason());
			logAuditEvent("PasswordlessRegistration", "Failure", "Registration Denied: " + ex.getReason(), null, request);
			return buildErrorResponse(ex.getReason(), ERROR_CODE_REGISTRATION_DENIED, HttpStatus.FORBIDDEN);
		} catch (UserAlreadyExistException ex) {
			// Anti-enumeration: the email is already registered, so we create NOTHING and publish no
			// registration event, but we return exactly the same generic 200 response a brand-new
			// passwordless registration would produce. The true reason is recorded server-side via the
			// audit event. This mirrors the form-registration path and prevents this endpoint from being
			// used to enumerate which email addresses are already registered (previously returned 409).
			//
			// Returning registrationPendingURI here mirrors what a genuine new (unverified) registration
			// returns in the default verification-enabled config, making both cases indistinguishable to
			// the caller. In verification-disabled / auto-login mode a real new registration additionally
			// establishes a session — that is an inherent, accepted difference that cannot be avoided
			// without skipping auto-login for legitimate new users.
			log.warn("User already exists with email: {}", dto.getEmail());
			logAuditEvent("PasswordlessRegistration", "Failure", "User Already Exists", null, request);
			return buildSuccessResponse("Registration Successful!", registrationPendingURI);
		} catch (Exception ex) {
			log.error("Unexpected error during passwordless registration.", ex);
			logAuditEvent("PasswordlessRegistration", "Failure", ex.getMessage(), null, request);
			return buildErrorResponse("System Error!", 5, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Sets an initial password for a passwordless account.
	 *
	 * <p>
	 * This endpoint only applies to passwordless (passkey-only) accounts and rejects the request if a password is already
	 * set (use {@code /user/updatePassword} to change an existing password, which requires the current password). Because
	 * the account has no current password to verify, this credential-altering operation cannot require re-authentication
	 * via a current password.
	 * </p>
	 *
	 * <p>
	 * <strong>Step-up (SUF-02):</strong> to address the residual risk that a session-only actor could add a durable
	 * password, this endpoint requires step-up when a {@link StepUpService} bean is provided, and is otherwise
	 * <strong>disabled by default</strong>. Set {@code user.security.allowInitialPasswordSetWithoutStepUp=true} to keep
	 * the previous session-only behavior when no step-up service is available. See MIGRATION.md.
	 * </p>
	 *
	 * @param userDetails the authenticated user details
	 * @param setPasswordDto the set password DTO
	 * @param request the HTTP servlet request
	 * @param locale the locale
	 * @return a ResponseEntity containing a JSONResponse with the result
	 */
	@PostMapping("/setPassword")
	public ResponseEntity<JSONResponse> setPassword(@AuthenticationPrincipal DSUserDetails userDetails,
			@Valid @RequestBody SetPasswordDto setPasswordDto, HttpServletRequest request, Locale locale) {
		validateAuthenticatedUser(userDetails);
		User user = userService.findUserByEmail(userDetails.getUser().getEmail());
		if (user == null) {
			return buildErrorResponse("User not found", 1, HttpStatus.BAD_REQUEST);
		}

		try {
			if (userService.hasPassword(user)) {
				return buildErrorResponse("User already has a password. Use the change password feature instead.", 1, HttpStatus.BAD_REQUEST);
			}

			// SUF-02: setting an initial password on a passwordless (passkey-only) account is a credential change with no
			// current credential to verify, so a session-only actor could otherwise add a durable password. If a consumer
			// supplies a StepUpService, require it to pass; otherwise the endpoint is disabled by default. Set
			// user.security.allowInitialPasswordSetWithoutStepUp=true to explicitly keep the session-only behavior.
			final StepUpService stepUpService = stepUpServiceProvider.getIfAvailable();
			if (stepUpService != null) {
				if (!stepUpService.isStepUpSatisfied(user, "set-password", request)) {
					logAuditEvent("SetPassword", "Failure", "Step-up verification failed", user, request);
					return buildErrorResponse(messages.getMessage("message.set-password.step-up-required", null,
							"Additional verification is required to set a password.", locale), 6, HttpStatus.UNAUTHORIZED);
				}
			} else if (!allowInitialPasswordSetWithoutStepUp) {
				logAuditEvent("SetPassword", "Failure", "Initial password set disabled (no step-up configured)", user, request);
				return buildErrorResponse(messages.getMessage("message.set-password.disabled", null,
						"Setting an initial password is not enabled on this server.", locale), 6, HttpStatus.FORBIDDEN);
			}

			if (!setPasswordDto.getNewPassword().equals(setPasswordDto.getConfirmPassword())) {
				return buildErrorResponse(messages.getMessage("message.password.mismatch", null, "Passwords do not match", locale), 2,
						HttpStatus.BAD_REQUEST);
			}

			List<String> errors = passwordPolicyService.validate(user, setPasswordDto.getNewPassword(), user.getEmail(), locale);
			if (!errors.isEmpty()) {
				log.warn("Password validation failed for user {}: {}", user.getEmail(), errors);
				return buildErrorResponse(String.join(" ", errors), 3, HttpStatus.BAD_REQUEST);
			}

			userService.setInitialPassword(user, setPasswordDto.getNewPassword());
			logAuditEvent("SetPassword", "Success", "Initial password set for passwordless account", user, request);

			return buildSuccessResponse(messages.getMessage("message.set-password.success", null, "Password set successfully", locale), null);
		} catch (Exception ex) {
			log.error("Unexpected error during set password.", ex);
			logAuditEvent("SetPassword", "Failure", ex.getMessage(), user, request);
			return buildErrorResponse("System Error!", 5, HttpStatus.INTERNAL_SERVER_ERROR);
		}
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
	 * @param user    the registered user
	 * @param request the HTTP servlet request
	 */
	private void publishRegistrationEvent(User user, HttpServletRequest request) {
		String appUrl = appUrlResolver.resolveAppUrl(request);
		// Capture immutable scalars from the still-attached entity so the @Async listener never touches a detached User.
		eventPublisher.publishEvent(OnRegistrationCompleteEvent.builder().userId(user.getId()).userEmail(user.getEmail())
				.userEnabled(user.isEnabled()).locale(request.getLocale()).appUrl(appUrl).build());
	}

	/**
	 * Logs an audit event.
	 *
	 * @param action  the action performed
	 * @param status  the status of the action
	 * @param message the message describing the action
	 * @param user    the user involved in the action
	 * @param request the HTTP servlet request
	 */
	private void logAuditEvent(String action, String status, String message, User user, HttpServletRequest request) {
		AuditEvent event = AuditEvent.builder().source(this).user(user).sessionId(request.getSession().getId())
				.ipAddress(UserUtils.getClientIP(request))
				.userAgent(request.getHeader("User-Agent")).action(action).actionStatus(status).message(message)
				.build();
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
		return ResponseEntity.status(status)
				.body(JSONResponse.builder().success(false).code(code).message(message).build());
	}

	/**
	 * Builds a success response.
	 *
	 * @param message
	 * @param redirectUrl
	 * @return a ResponseEntity containing a JSONResponse with the success response
	 */
	private ResponseEntity<JSONResponse> buildSuccessResponse(String message, String redirectUrl) {
		return ResponseEntity
				.ok(JSONResponse.builder().success(true).code(0).message(message).redirectUrl(redirectUrl).build());
	}
}
