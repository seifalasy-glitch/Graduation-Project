package com.edusync.edusync.backend.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.server.ResponseStatusException;
import com.edusync.edusync.backend.admin.exception.DuplicateUserException;
import org.springframework.dao.DataIntegrityViolationException;
import java.sql.SQLException;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(
            IllegalArgumentException exception
    ) {
        String message = "Invalid request data";

        if ("New password must be different from current password"
                .equals(exception.getMessage())) {
            message = "New password must be different from current password";
        }

        return error(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleBadCredentials(
            BadCredentialsException exception,
            HttpServletRequest request
    ) {
        String message = "Invalid credentials";

        if ("/api/auth/login".equals(request.getServletPath())) {
            message = "Invalid email or password";
        }

        return error(HttpStatus.UNAUTHORIZED, message);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(
            MethodArgumentNotValidException exception
    ) {
        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::validationMessage)
                .findFirst()
                .orElse("Invalid request data");

        return error(HttpStatus.BAD_REQUEST, message);
    }

    private String validationMessage(FieldError fieldError) {
        String field = fieldError.getField();
        String code = fieldError.getCode();

        if ("email".equals(field)) {
            if ("NotBlank".equals(code)) {
                return "Email is required";
            }

            if ("Email".equals(code)) {
                return "Invalid email format";
            }
        }

        if ("password".equals(field) && "NotBlank".equals(code)) {
            return "Password is required";
        }

        if ("currentPassword".equals(field) && "NotBlank".equals(code)) {
            return "Current password is required";
        }

        if ("newPassword".equals(field)) {
            if ("NotBlank".equals(code)) {
                return "New password is required";
            }

            if ("Size".equals(code)) {
                return "Password must be between 8 and 72 characters";
            }
        }

        return "Invalid request data";
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleInvalidJson(
            HttpMessageNotReadableException exception
    ) {
        return error(HttpStatus.BAD_REQUEST, "Invalid JSON format");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(
            NoResourceFoundException exception
    ) {
        return error(HttpStatus.NOT_FOUND, "Endpoint not found");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, String>> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException exception
    ) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpectedError(Exception ex) {
        ex.printStackTrace();
        return error(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred"
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(
            AccessDeniedException ex
    ) {
        return error(
                HttpStatus.FORBIDDEN,
                "Access denied"
        );
    }

    @ExceptionHandler(DuplicateUserException.class)
    public ResponseEntity<Map<String, String>> handleDuplicateUser(
            DuplicateUserException ex
    ) {
        return error(
                HttpStatus.CONFLICT,
                ex.getMessage()
        );
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(
            ResponseStatusException ex
    ) {
        String message = ex.getReason() != null
                ? ex.getReason()
                : "Request failed";

        return ResponseEntity
                .status(ex.getStatusCode())
                .body(Map.of("message", message));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrityViolation(
            DataIntegrityViolationException ex
    ) {
        Throwable cause = ex;

        while (cause != null) {

            if (cause instanceof SQLException sqlException) {

                if ("23505".equals(sqlException.getSQLState())) {
                    return error(
                            HttpStatus.CONFLICT,
                            "A database record with these values already exists"
                    );
                }
            }

            cause = cause.getCause();
        }

        return error(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred"
        );
    }
    private ResponseEntity<Map<String, String>> error(
            HttpStatus status,
            String message
    ) {
        return ResponseEntity
                .status(status)
                .body(Map.of("message", message));
    }
}
