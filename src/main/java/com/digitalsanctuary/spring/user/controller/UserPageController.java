package com.digitalsanctuary.spring.user.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import com.digitalsanctuary.spring.user.dto.UserDto;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.service.DSUserDetails;
import com.digitalsanctuary.spring.user.web.IncludeUserInModel;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * MVC controller that serves user management HTML pages.
 * <p>
 * Handles page rendering for login, registration, password reset,
 * profile update, and account management views. Each method returns
 * a Thymeleaf template name for the corresponding user interface page.
 * </p>
 *
 * @author Digital Sanctuary
 */
@Slf4j
@RequiredArgsConstructor
@Controller
@IncludeUserInModel
public class UserPageController {

	@Value("${user.registration.facebookEnabled}")
	private boolean facebookEnabled;

	@Value("${user.registration.googleEnabled}")
	private boolean googleEnabled;

	@Value("${user.registration.keycloakEnabled}")
	private boolean keycloakEnabled;

	/**
	 * Login Page.
	 *
	 * @param userDetails the user details
	 * @param session the session
	 * @param model the model
	 *
	 * @return the string
	 */
	@GetMapping("${user.security.loginPageURI:/user/login.html}")
	public String login(@AuthenticationPrincipal DSUserDetails userDetails, HttpSession session, ModelMap model) {
		log.debug("UserPageController.login: userDetails: {}", userDetails);
		if (session != null && session.getAttribute("error.message") != null) {
			model.addAttribute("errormessage", session.getAttribute("error.message"));
			session.removeAttribute("error.message");
		}
		model.addAttribute("googleEnabled", googleEnabled);
		model.addAttribute("facebookEnabled", facebookEnabled);
		model.addAttribute("keycloakEnabled", keycloakEnabled);
		return "user/login";
	}

	/**
	 * Register Page.
	 *
	 * @param userDetails the user details
	 * @param session the session
	 * @param model the model
	 * @return the string
	 */
	@GetMapping("${user.security.registrationURI:/user/register.html}")
	public String register(@AuthenticationPrincipal DSUserDetails userDetails, HttpSession session, ModelMap model) {
		log.debug("UserPageController.register: userDetails: {}", userDetails);
		if (session != null && session.getAttribute("error.message") != null) {
			model.addAttribute("errormessage", session.getAttribute("error.message"));
			session.removeAttribute("error.message");
		}
		model.addAttribute("googleEnabled", googleEnabled);
		model.addAttribute("facebookEnabled", facebookEnabled);
		model.addAttribute("keycloakEnabled", keycloakEnabled);
		return "user/register";
	}

	/**
	 * Registration pending.
	 *
	 * @return the string
	 */
	@GetMapping("${user.security.registrationPendingURI:/user/registration-pending-verification.html}")
	public String registrationPending() {
		return "user/registration-pending-verification";
	}

	/**
	 * Registration complete.
	 *
	 * @param userDetails the user details
	 * @param session the session
	 * @param model the model
	 *
	 * @return the string
	 */
	@GetMapping("${user.security.registrationSuccessURI:/user/registration-complete.html}")
	public String registrationComplete(@AuthenticationPrincipal DSUserDetails userDetails, HttpSession session,
			ModelMap model) {
		log.debug("UserPageController.registrationComplete: userDetails: {}", userDetails);
		return "user/registration-complete";
	}

	/**
	 * Request new verification E mail.
	 *
	 * @return the string
	 */
	@GetMapping("${user.security.registrationNewVerificationURI:/user/request-new-verification-email.html}")
	public String requestNewVerificationEMail() {
		return "user/request-new-verification-email";
	}

	/**
	 * Forgot password.
	 *
	 * @return the string
	 */
	@GetMapping("${user.security.forgotPasswordURI:/user/forgot-password.html}")
	public String forgotPassword() {
		return "user/forgot-password";
	}

	/**
	 * Forgot password pending verification.
	 *
	 * @return the string
	 */
	@GetMapping("${user.security.forgotPasswordPendingURI:/user/forgot-password-pending-verification.html}")
	public String forgotPasswordPendingVerification() {
		return "user/forgot-password-pending-verification";
	}

	/**
	 * Forgot password change.
	 *
	 * @return the string
	 */
	@GetMapping("${user.security.forgotPasswordChangeURI:/user/forgot-password-change.html}")
	public String forgotPasswordChange() {
		return "user/forgot-password-change";
	}


	/**
	 * Displays the user profile update page with pre-populated user data.
	 *
	 * @param userDetails the user details
	 * @param request the request
	 * @param model the model
	 * @return the view name for the update user page
	 */
	@GetMapping("${user.security.updateUserURI:/user/update-user.html}")
	public String updateUser(@AuthenticationPrincipal DSUserDetails userDetails, final HttpServletRequest request,
			final ModelMap model) {
		if (userDetails != null) {
			User user = userDetails.getUser();
			UserDto userDto = new UserDto();
			userDto.setFirstName(user.getFirstName());
			userDto.setLastName(user.getLastName());
			model.addAttribute("user", userDto);
		}
		return "user/update-user";
	}

	/**
	 * Update password.
	 *
	 * @return the string
	 */
	@GetMapping("${user.security.updatePasswordURI:/user/update-password.html}")
	public String updatePassword() {
		return "user/update-password";
	}

	/**
	 * Delete account.
	 *
	 * @return the string
	 */
	@GetMapping("${user.security.deleteAccountURI:/user/delete-account.html}")
	public String deleteAccount() {
		return "user/delete-account";
	}

}
