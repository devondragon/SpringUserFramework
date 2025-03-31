package com.digitalsanctuary.spring.user.util;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Utility class for creating JSON response.
 */
public class ResponseUtil {

    /**
     * Builds an error response.
     *
     * @param message
     * @param code
     * @param status
     * @return a ResponseEntity containing a JSONResponse with the error response
     */
    public static ResponseEntity<JSONResponse> buildErrorResponse(String message, int code, HttpStatus status) {
        return ResponseEntity.status(status).body(JSONResponse.builder().success(false).code(code).message(message).build());
    }

    /**
     * Builds a success response.
     *
     * @param message
     * @param redirectUrl
     * @return a ResponseEntity containing a JSONResponse with the success response
     */
    public static ResponseEntity<JSONResponse> buildSuccessResponse(String message, String redirectUrl) {
        return ResponseEntity.ok(JSONResponse.builder().success(true).code(0).message(message).redirectUrl(redirectUrl).build());
    }
}
