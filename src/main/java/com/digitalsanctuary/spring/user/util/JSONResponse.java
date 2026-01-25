package com.digitalsanctuary.spring.user.util;

import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

/**
 * Standardized JSON response object for REST API endpoints.
 * <p>
 * Provides a consistent structure for API responses including success status, messages,
 * redirect URLs, and data payloads. Use the Lombok-generated builder to construct instances.
 * </p>
 * <p>
 * Example usage:
 * </p>
 * <pre>
 * JSONResponse response = JSONResponse.builder()
 *     .success(true)
 *     .message("Operation completed")
 *     .message("Data saved successfully")
 *     .data(resultObject)
 *     .build();
 * </pre>
 *
 * @author Devon Hillard
 * @version 1.0
 */
@Getter
@Builder
public class JSONResponse {

	/**
	 * Indicates the success or failure of the API call.
	 */
	private final boolean success;

	/**
	 * The URL to which a client should be redirected (if applicable). This can be null if no redirection is needed.
	 */
	private final String redirectUrl;

	/**
	 * The HTTP status code associated with the response. This can be null if the default status code is used.
	 */
	private final Integer code;

	/**
	 * A list of messages associated with the API response. This can be used for sending information, error messages, or other relevant notifications
	 * to the client.
	 */
	@Singular
	private final List<String> messages;

	/**
	 * The main data payload of the response. This can be any type of object representing the data to be sent to the client.
	 */
	private final Object data;

}
