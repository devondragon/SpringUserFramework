package com.digitalsanctuary.spring.user.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.digitalsanctuary.spring.user.exceptions.WebAuthnException;
import com.digitalsanctuary.spring.user.exceptions.WebAuthnUserNotFoundException;
import com.digitalsanctuary.spring.user.util.GenericResponse;

/**
 * Centralized exception handling for WebAuthn credential management endpoints.
 */
@RestControllerAdvice(assignableTypes = WebAuthnManagementAPI.class)
public class WebAuthnManagementAPIAdvice {

	@ExceptionHandler(WebAuthnUserNotFoundException.class)
	public ResponseEntity<GenericResponse> handleUserNotFound(WebAuthnUserNotFoundException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new GenericResponse(ex.getMessage()));
	}

	@ExceptionHandler(WebAuthnException.class)
	public ResponseEntity<GenericResponse> handleWebAuthnError(WebAuthnException ex) {
		return ResponseEntity.badRequest().body(new GenericResponse(ex.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<GenericResponse> handleValidation(MethodArgumentNotValidException ex) {
		return ResponseEntity.badRequest().body(new GenericResponse(ex.getBindingResult().getAllErrors(), "Validation failed"));
	}
}
