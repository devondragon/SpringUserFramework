package com.digitalsanctuary.spring.user.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.digitalsanctuary.spring.user.exceptions.WebAuthnAccountLockedException;
import com.digitalsanctuary.spring.user.exceptions.WebAuthnException;
import com.digitalsanctuary.spring.user.exceptions.WebAuthnReauthenticationException;
import com.digitalsanctuary.spring.user.exceptions.WebAuthnStepUpRequiredException;
import com.digitalsanctuary.spring.user.exceptions.WebAuthnUserNotFoundException;
import com.digitalsanctuary.spring.user.util.GenericResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

/**
 * Centralized exception handling for WebAuthn credential management endpoints.
 */
@RestControllerAdvice(assignableTypes = WebAuthnManagementAPI.class)
@ConditionalOnProperty(name = "user.webauthn.enabled", havingValue = "true", matchIfMissing = false)
@Slf4j
public class WebAuthnManagementAPIAdvice {

	@ExceptionHandler(WebAuthnUserNotFoundException.class)
	public ResponseEntity<GenericResponse> handleUserNotFound(WebAuthnUserNotFoundException ex) {
		log.warn("WebAuthn user not found: {}", ex.getMessage());
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new GenericResponse(ex.getMessage()));
	}

	@ExceptionHandler(WebAuthnAccountLockedException.class)
	public ResponseEntity<GenericResponse> handleAccountLocked(WebAuthnAccountLockedException ex) {
		log.warn("WebAuthn account locked: {}", ex.getMessage());
		return ResponseEntity.status(HttpStatus.LOCKED).body(new GenericResponse(ex.getMessage()));
	}

	@ExceptionHandler(WebAuthnReauthenticationException.class)
	public ResponseEntity<GenericResponse> handleReauthenticationFailure(WebAuthnReauthenticationException ex) {
		log.warn("WebAuthn re-authentication failure: {}", ex.getMessage());
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new GenericResponse(ex.getMessage()));
	}

	@ExceptionHandler(WebAuthnStepUpRequiredException.class)
	public ResponseEntity<GenericResponse> handleStepUpRequired(WebAuthnStepUpRequiredException ex) {
		log.warn("WebAuthn step-up required: {}", ex.getMessage());
		// A distinct error code, so a client can launch its login ceremony and retry rather than prompting for a
		// password it may not have.
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(new GenericResponse(ex.getMessage(), WebAuthnStepUpRequiredException.ERROR_CODE));
	}

	@ExceptionHandler(WebAuthnException.class)
	public ResponseEntity<GenericResponse> handleWebAuthnError(WebAuthnException ex) {
		log.warn("WebAuthn error: {}", ex.getMessage());
		return ResponseEntity.badRequest().body(new GenericResponse(ex.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<GenericResponse> handleValidation(MethodArgumentNotValidException ex) {
		log.warn("WebAuthn validation error: {}", ex.getMessage());
		return ResponseEntity.badRequest().body(new GenericResponse(ex.getBindingResult().getAllErrors(), "Validation failed"));
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<GenericResponse> handleConstraintViolation(ConstraintViolationException ex) {
		log.warn("WebAuthn constraint violation: {}", ex.getMessage());
		return ResponseEntity.badRequest().body(new GenericResponse("Validation failed"));
	}
}
