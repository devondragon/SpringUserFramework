package com.digitalsanctuary.spring.user.util;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import lombok.Data;

/**
 * A generic response class used to encapsulate response messages and errors. This class is typically used to provide feedback to the client in a
 * structured format. It can handle simple messages as well as validation errors.
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>
 * {@code
 * GenericResponse response = new GenericResponse("Success");
 * }
 * </pre>
 *
 * <p>
 * Example usage with errors:
 * </p>
 *
 * <pre>
 * {@code
 * List<ObjectError> errors = ...;
 * GenericResponse response = new GenericResponse(errors, "Validation failed");
 * }
 * </pre>
 */
@Data
public class GenericResponse {
	/**
	 * The message to be conveyed in the response.
	 */
	private String message;

	/**
	 * The error message, if any, associated with the response.
	 */
	private String error;

	/**
	 * Constructs a new GenericResponse with the specified message.
	 *
	 * @param message the message to be conveyed in the response
	 */
	public GenericResponse(final String message) {
		super();
		this.message = message;
	}

	/**
	 * Constructs a new GenericResponse with the specified message and error.
	 *
	 * @param message the message to be conveyed in the response
	 * @param error the error message associated with the response
	 */
	public GenericResponse(final String message, final String error) {
		super();
		this.message = message;
		this.error = error;
	}

	/**
	 * Constructs a new GenericResponse with the specified list of validation errors and error message. The validation errors are converted to a
	 * JSON-like string format.
	 *
	 * @param allErrors the list of validation errors
	 * @param error the error message associated with the response
	 */
	public GenericResponse(List<ObjectError> allErrors, String error) {
		this.error = error;
		String temp = allErrors.stream().map(e -> {
			if (e instanceof FieldError) {
				return "{\"field\":\"" + ((FieldError) e).getField() + "\",\"defaultMessage\":\"" + e.getDefaultMessage() + "\"}";
			} else {
				return "{\"object\":\"" + e.getObjectName() + "\",\"defaultMessage\":\"" + e.getDefaultMessage() + "\"}";
			}
		}).collect(Collectors.joining(","));
		this.message = "[" + temp + "]";
	}
}
