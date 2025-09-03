package com.digitalsanctuary.spring.user.util;

import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

/**
 * Represents a standardized JSON response for API calls.
 * <p>
 * This class provides a builder to facilitate the creation of JSON response objects with various attributes.
 * The builder uses the {@code @Singular} annotation on the messages field, which means you can call 
 * {@code .message(String)} multiple times to add messages to the list, and the JSON will serialize 
 * as {@code "messages": ["message1", "message2", ...]}. 
 * </p>
 * <p>
 * Example usage:
 * <pre>
 * JSONResponse response = JSONResponse.builder()
 *     .success(true)
 *     .message("Operation completed")
 *     .message("Data saved successfully")
 *     .data(resultObject)
 *     .build();
 * // Results in JSON: {"success": true, "messages": ["Operation completed", "Data saved successfully"], ...}
 * </pre>
 * </p>
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
