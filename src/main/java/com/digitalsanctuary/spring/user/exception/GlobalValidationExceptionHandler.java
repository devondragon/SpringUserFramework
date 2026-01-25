package com.digitalsanctuary.spring.user.exception;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import lombok.extern.slf4j.Slf4j;

/**
 * Global exception handler for validation errors across all API endpoints.
 * Handles {@link MethodArgumentNotValidException} thrown by {@code @Valid} annotations
 * and returns structured error responses with field-level validation details.
 */
@Slf4j
@ControllerAdvice
public class GlobalValidationExceptionHandler {

	/**
	 * Handles validation errors from @Valid annotations on request bodies.
	 *
	 * @param ex the MethodArgumentNotValidException
	 * @return a ResponseEntity containing validation error details
	 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, Object>> handleValidationExceptions(MethodArgumentNotValidException ex) {
		log.warn("Validation error occurred: {}", ex.getMessage());
		
		Map<String, Object> response = new HashMap<>();
		Map<String, String> errors = new HashMap<>();
		
		ex.getBindingResult().getAllErrors().forEach((error) -> {
			String fieldName = ((FieldError) error).getField();
			String errorMessage = error.getDefaultMessage();
			errors.put(fieldName, errorMessage);
		});
		
		response.put("success", false);
		response.put("code", 400);
		response.put("message", "Validation failed");
		response.put("errors", errors);
		
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
	}
}