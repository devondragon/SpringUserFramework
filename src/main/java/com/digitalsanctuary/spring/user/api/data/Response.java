package com.digitalsanctuary.spring.user.api.data;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * Represents a response object used in API testing.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Response {
    private Integer code = 0; // Ensure code defaults to 0

    public void setCode(Integer code) {
        this.code = code != null ? code : 0; // Handle null values explicitly
    }

    private String message;
    private String nextURL;
}
