package com.edusync.edusync.backend.common;

import org.springframework.http.HttpStatus;

/**
 * An error with its own code from the API contract, e.g.
 * throw new ApiException(HttpStatus.BAD_REQUEST, "WRONG_PASSWORD", "The current password is wrong");
 * GlobalExceptionHandler turns it into { "code": "WRONG_PASSWORD", "message": "..." }.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
