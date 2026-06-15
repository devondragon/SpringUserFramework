package com.digitalsanctuary.spring.user.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.ControllerAdvice;

import com.digitalsanctuary.spring.user.api.GdprAPI;
import com.digitalsanctuary.spring.user.api.MfaAPI;
import com.digitalsanctuary.spring.user.api.UserAPI;
import com.digitalsanctuary.spring.user.api.WebAuthnManagementAPI;
import com.digitalsanctuary.spring.user.controller.UserActionController;
import com.digitalsanctuary.spring.user.controller.UserPageController;

/**
 * Verifies that {@link GlobalValidationExceptionHandler} is scoped to the library's own controllers
 * via {@code @ControllerAdvice(assignableTypes = ...)} rather than applying application-wide.
 */
@DisplayName("GlobalValidationExceptionHandler scope")
class GlobalValidationExceptionHandlerScopeTest {

	@Test
	@DisplayName("Should be scoped to the library's own controllers via assignableTypes")
	void shouldScopeAdviceToLibraryControllers() {
		ControllerAdvice annotation = GlobalValidationExceptionHandler.class.getAnnotation(ControllerAdvice.class);

		assertThat(annotation).as("@ControllerAdvice must be present").isNotNull();
		assertThat(annotation.assignableTypes())
				.as("Advice must be scoped to the library's own controllers")
				.containsExactlyInAnyOrder(UserAPI.class, GdprAPI.class, MfaAPI.class, UserActionController.class, UserPageController.class);
	}

	@Test
	@DisplayName("Should NOT target WebAuthnManagementAPI (it has its own dedicated advice)")
	void shouldNotTargetWebAuthnManagementApi() {
		ControllerAdvice annotation = GlobalValidationExceptionHandler.class.getAnnotation(ControllerAdvice.class);

		assertThat(annotation.assignableTypes())
				.as("WebAuthnManagementAPI is handled by its dedicated advice and must be excluded")
				.doesNotContain(WebAuthnManagementAPI.class);
	}

	@Test
	@DisplayName("Should NOT apply application-wide (no basePackages, annotations, or unrestricted scope)")
	void shouldNotApplyApplicationWide() {
		ControllerAdvice annotation = GlobalValidationExceptionHandler.class.getAnnotation(ControllerAdvice.class);

		assertThat(annotation.assignableTypes())
				.as("assignableTypes must be set so the advice is not global")
				.isNotEmpty();
		assertThat(annotation.basePackages()).as("No basePackages scope").isEmpty();
		assertThat(annotation.basePackageClasses()).as("No basePackageClasses scope").isEmpty();
		assertThat(annotation.annotations()).as("No annotation-based scope").isEmpty();
	}
}
