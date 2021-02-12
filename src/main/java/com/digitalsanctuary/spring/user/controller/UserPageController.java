package com.digitalsanctuary.spring.user.controller;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;

import com.digitalsanctuary.spring.user.dto.UserDto;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.service.DSUserDetails;

/**
 * The UserPageController for the user management pages.
 */
@Controller
public class UserPageController {
	/** The logger. */
	public Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * Login Page.
	 *
	 * @param userDetails
	 *            the user details
	 * @return the string
	 */
	@GetMapping("/user/login.html")
	public String login(@AuthenticationPrincipal DSUserDetails userDetails) {
		logger.debug("UserPageController.login:" + "userDetails: {}", userDetails);
		return "user/login";
	}

	/**
	 * Register Page.
	 *
	 * @return the string
	 */
	@GetMapping("/user/register.html")
	public String register() {
		return "user/register";
	}

	/**
	 * Registration pending.
	 *
	 * @return the string
	 */
	@GetMapping("/user/registration-pending-verification.html")
	public String registrationPending() {
		return "user/registration-pending-verification";
	}

	/**
	 * Registration complete.
	 *
	 * @return the string
	 */
	@GetMapping("/user/registration-complete.html")
	public String registrationComplete() {
		return "user/registration-complete";
	}

	/**
	 * Request new verification E mail.
	 *
	 * @return the string
	 */
	@GetMapping("/user/request-new-verification-email.html")
	public String requestNewVerificationEMail() {
		return "user/request-new-verification-email";
	}

	/**
	 * Forgot password.
	 *
	 * @return the string
	 */
	@GetMapping("/user/forgot-password.html")
	public String forgotPassword() {
		return "user/forgot-password";
	}

	/**
	 * Forgot password pending verification.
	 *
	 * @return the string
	 */
	@GetMapping("/user/forgot-password-pending-verification.html")
	public String forgotPasswordPendingVerification() {
		return "user/forgot-password-pending-verification";
	}

	/**
	 * Forgot password change.
	 *
	 * @return the string
	 */
	@GetMapping("/user/forgot-password-change.html")
	public String forgotPasswordChange() {
		return "user/forgot-password-change";
	}

	@GetMapping("/user/update-user.html")
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

	@GetMapping("/user/update-password.html")
	public String updatePassword() {
		return "user/update-password";
	}

}
