package com.digitalsanctuary.spring.user.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.digitalsanctuary.spring.user.exceptions.WebAuthnException;
import com.digitalsanctuary.spring.user.exceptions.WebAuthnUserNotFoundException;
import com.digitalsanctuary.spring.user.util.GenericResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

/**
 * Centralized exception handling for WebAuthn credential management endpoints.
 */
@RestControllerAdvice(assignableTypes = WebAuthnManagementAPI.class)
@Slf4j
public class WebAuthnManagementAPIAdvice {

	@ExceptionHandler(WebAuthnUserNotFoundException.class)
	public ResponseEntity<GenericResponse> handleUserNotFound(WebAuthnUserNotFoundException ex) {
		log.warn("WebAuthn user not found: {}", ex.getMessage(), ex);
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new GenericResponse(ex.getMessage()));
	}

	@ExceptionHandler(WebAuthnException.class)
	public ResponseEntity<GenericResponse> handleWebAuthnError(WebAuthnException ex) {
		log.warn("WebAuthn error: {}", ex.getMessage(), ex);
		return ResponseEntity.badRequest().body(new GenericResponse(ex.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<GenericResponse> handleValidation(MethodArgumentNotValidException ex) {
		log.warn("WebAuthn validation error: {}", ex.getMessage(), ex);
		return ResponseEntity.badRequest().body(new GenericResponse(ex.getBindingResult().getAllErrors(), "Validation failed"));
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<GenericResponse> handleConstraintViolation(ConstraintViolationException ex) {
		log.warn("WebAuthn constraint violation: {}", ex.getMessage(), ex);
		return ResponseEntity.badRequest().body(new GenericResponse("Validation failed"));
	}
}
