package com.edusync.edusync.backend.common;

import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An error with its own code from the API contract, e.g.
 * throw new ApiException(HttpStatus.BAD_REQUEST, "WRONG_PASSWORD", "The current password is wrong");
 * GlobalExceptionHandler turns it into { "code": "WRONG_PASSWORD", "message": "..." }.
 *
 * Optional `details` (e.g. which field is wrong) is sent as { "details": { ... } }.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Map<String, Object> details;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    public ApiException(
            HttpStatus status,
            String code,
            String message,
            Map<String, Object> details
    ) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details;
    }

    /** Shortcut for the usual details: { "field": "email", "problem": "already in use" } */
    public static Map<String, Object> fieldDetails(String field, String problem) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("field", field);
        details.put("problem", problem);
        return details;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
