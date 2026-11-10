package com.edusync.edusync.backend.common;

import com.edusync.edusync.backend.admin.exception.DuplicateUserException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every error leaves the backend in the shape the API contract asks for:
 * { "code": "SOME_CODE", "message": "A sentence the user can read" }
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ---------- Errors with their own code from the API contract ----------

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, String>> handleApiException(ApiException ex) {
        return error(ex.getStatus(), ex.getCode(), ex.getMessage());
    }

    // ---------- Wrong data sent by the user (400) ----------

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(
            IllegalArgumentException exception
    ) {
        // Our services write readable messages ("Invalid roles", "Column 1 must be UniversityId"...),
        // so the user gets the real reason instead of a generic text
        String message = exception.getMessage() != null
                ? exception.getMessage()
                : "Invalid request data";

        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(
            MethodArgumentNotValidException exception
    ) {
        FieldError fieldError = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .orElse(null);

        if (fieldError == null) {
            return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid request data");
        }

        // API contract: a new password that breaks the rules is WEAK_PASSWORD
        boolean weakPassword = "newPassword".equals(fieldError.getField())
                && ("Pattern".equals(fieldError.getCode()) || "Size".equals(fieldError.getCode()));

        String code = weakPassword ? "WEAK_PASSWORD" : "VALIDATION_ERROR";

        return error(HttpStatus.BAD_REQUEST, code, validationMessage(fieldError));
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
                return "Password must be at most 72 characters";
            }

            if ("Pattern".equals(code)) {
                return "Password must have at least 8 characters with an uppercase letter, "
                        + "a lowercase letter, a digit and a symbol";
            }
        }

        // Any other field: say which field is wrong
        if ("NotBlank".equals(code) || "NotNull".equals(code) || "NotEmpty".equals(code)) {
            return field + " is required";
        }

        return field + " is invalid";
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleInvalidJson(
            HttpMessageNotReadableException exception
    ) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_JSON", "Invalid JSON format");
    }

    @ExceptionHandler({
            MissingServletRequestPartException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<Map<String, String>> handleMissingPart(Exception exception) {
        String name = exception instanceof MissingServletRequestPartException part
                ? part.getRequestPartName()
                : ((MissingServletRequestParameterException) exception).getParameterName();

        return error(HttpStatus.BAD_REQUEST, "MISSING_FIELD", name + " is required");
    }

    // ---------- File uploads ----------

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleFileTooLarge(
            MaxUploadSizeExceededException exception
    ) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE",
                "The file is larger than 10 MB");
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Map<String, String>> handleMultipart(MultipartException exception) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "Send the file as multipart/form-data");
    }

    // ---------- Login and permissions ----------

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleBadCredentials(
            BadCredentialsException exception,
            HttpServletRequest request
    ) {
        String message = "Invalid credentials";

        if ("/api/auth/login".equals(request.getServletPath())) {
            message = "Invalid email or password";
        }

        return error(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", message);
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<Map<String, String>> handleDisabled(DisabledException ex) {
        return error(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED",
                "This account is disabled. Please contact the admin");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException ex) {
        return error(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied");
    }

    // ---------- Not found / wrong method ----------

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(NoResourceFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "Endpoint not found");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, String>> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException exception
    ) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "Method not allowed");
    }

    // ---------- Duplicates and conflicts (409) ----------

    @ExceptionHandler(DuplicateUserException.class)
    public ResponseEntity<Map<String, String>> handleDuplicateUser(DuplicateUserException ex) {
        return error(HttpStatus.CONFLICT, "DUPLICATE", ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrityViolation(
            DataIntegrityViolationException ex
    ) {
        Throwable cause = ex;

        while (cause != null) {
            if (cause instanceof SQLException sqlException
                    && "23505".equals(sqlException.getSQLState())) {
                return error(HttpStatus.CONFLICT, "DUPLICATE",
                        "A database record with these values already exists");
            }

            cause = cause.getCause();
        }

        log.error("Database error", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred");
    }

    // ---------- Errors thrown with a status, e.g. ResponseStatusException(NOT_FOUND, "User not found") ----------

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException ex) {
        String reason = ex.getReason() != null ? ex.getReason() : "Request failed";

        // A reason already written as a code (e.g. "INVALID_OR_EXPIRED_TOKEN") is used as the code;
        // a normal sentence (e.g. "User not found") gets a code from the status
        String code = reason.matches("[A-Z_]+")
                ? reason
                : codeFor(ex.getStatusCode());

        return error(ex.getStatusCode(), code, reason);
    }

    // ---------- Anything else (500) ----------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpectedError(Exception ex) {
        log.error("Unexpected error", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred");
    }

    // ---------- Helpers ----------

    private String codeFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "VALIDATION_ERROR";
            case 401 -> "UNAUTHORIZED";
            case 403 -> "FORBIDDEN";
            case 404 -> "NOT_FOUND";
            case 409 -> "CONFLICT";
            case 429 -> "TOO_MANY_REQUESTS";
            default -> "INTERNAL_ERROR";
        };
    }

    private ResponseEntity<Map<String, String>> error(
            HttpStatusCode status,
            String code,
            String message
    ) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);

        return ResponseEntity.status(status).body(body);
    }
}