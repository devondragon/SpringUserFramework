package com.digitalsanctuary.spring.user.exception;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import com.digitalsanctuary.spring.user.api.GdprAPI;
import com.digitalsanctuary.spring.user.api.MfaAPI;
import com.digitalsanctuary.spring.user.api.UserAPI;
import com.digitalsanctuary.spring.user.controller.UserActionController;
import com.digitalsanctuary.spring.user.controller.UserPageController;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

/**
 * Validation exception handler for the framework's own controllers.
 *
 * <p>
 * This advice is intentionally <strong>scoped</strong> to the library's controllers via
 * {@code assignableTypes}. It does not apply application-wide, so it will never intercept (or reformat)
 * validation errors raised by a consuming application's own controllers. Consuming applications that
 * want uniform validation error formatting for THEIR controllers must provide their own
 * {@code @ControllerAdvice}.
 * </p>
 *
 * <p>
 * {@code WebAuthnManagementAPI} is deliberately excluded: it has its own dedicated advice
 * ({@code WebAuthnManagementAPIAdvice}) which already handles {@link MethodArgumentNotValidException}
 * and {@link ConstraintViolationException}. Including it here would create two advices targeting the
 * same controller with overlapping {@code @ExceptionHandler} types and ambiguous resolution.
 * </p>
 *
 * <p>
 * Handles {@link MethodArgumentNotValidException} (both field-level and class-level/global errors,
 * such as the class-level {@code @PasswordMatches} constraint on {@code UserDto}) and
 * {@link ConstraintViolationException}, returning a structured HTTP 400 response.
 * </p>
 */
@Slf4j
@ControllerAdvice(assignableTypes = {UserAPI.class, GdprAPI.class, MfaAPI.class, UserActionController.class, UserPageController.class})
public class GlobalValidationExceptionHandler {

	/**
	 * Handles validation errors from {@code @Valid} annotations on request bodies.
	 *
	 * <p>
	 * Both field-level errors and global (class-level) errors are collected. Class-level constraints
	 * such as {@code @PasswordMatches} produce a global error rather than a field error; collecting
	 * the global errors surfaces them in the response as a structured 400 instead of failing with a
	 * 500.
	 * </p>
	 *
	 * @param ex the MethodArgumentNotValidException
	 * @return a ResponseEntity containing validation error details
	 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, Object>> handleValidationExceptions(MethodArgumentNotValidException ex) {
		log.warn("Validation error occurred: {}", ex.getMessage());

		Map<String, String> errors = new HashMap<>();

		ex.getBindingResult().getFieldErrors().forEach((error) -> {
			errors.put(error.getField(), error.getDefaultMessage());
		});

		ex.getBindingResult().getGlobalErrors().forEach((error) -> {
			errors.put(globalErrorKey(error), error.getDefaultMessage());
		});

		return badRequest(errors);
	}

	/**
	 * Handles {@link ConstraintViolationException} raised outside of method-argument binding, for
	 * example by {@code @Validated} on controller method parameters.
	 *
	 * @param ex the ConstraintViolationException
	 * @return a ResponseEntity containing validation error details
	 */
	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<Map<String, Object>> handleConstraintViolationException(ConstraintViolationException ex) {
		log.warn("Constraint violation occurred: {}", ex.getMessage());

		Map<String, String> errors = new HashMap<>();

		if (ex.getConstraintViolations() != null) {
			for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
				String key = violation.getPropertyPath() != null ? violation.getPropertyPath().toString() : "error";
				errors.put(key, violation.getMessage());
			}
		}

		return badRequest(errors);
	}

	/**
	 * Builds the structured 400 response body shared by all validation handlers, preserving the
	 * response shape (success, code, message, errors).
	 *
	 * @param errors the collected validation errors keyed by field/object name
	 * @return a 400 ResponseEntity with the structured body
	 */
	private ResponseEntity<Map<String, Object>> badRequest(Map<String, String> errors) {
		Map<String, Object> response = new HashMap<>();
		response.put("success", false);
		response.put("code", 400);
		response.put("message", "Validation failed");
		response.put("errors", errors);
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
	}

	/**
	 * Derives a stable key for a global (class-level) error. Falls back to the object name when no
	 * specific error code is available.
	 *
	 * @param error the global ObjectError
	 * @return a key for the errors map
	 */
	private String globalErrorKey(ObjectError error) {
		if (error instanceof FieldError fieldError) {
			return fieldError.getField();
		}
		String code = error.getCode();
		return code != null ? code : error.getObjectName();
	}
}
