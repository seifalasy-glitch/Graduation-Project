package com.edusync.edusync.backend.academic.exception;

import com.edusync.edusync.backend.common.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Single place for every error the academic module can return.
 * Each one becomes { "code": "...", "message": "...", "details": { ... } } through GlobalExceptionHandler.
 */
public final class AcademicErrors {

    private AcademicErrors() {
    }

    // ---------- 404 ----------
    public static ApiException programNotFound() {
        return of(HttpStatus.NOT_FOUND, "PROGRAM_NOT_FOUND", "Academic program not found");
    }

    public static ApiException termNotFound() {
        return of(HttpStatus.NOT_FOUND, "TERM_NOT_FOUND", "Academic term not found");
    }

    public static ApiException courseNotFound() {
        return of(HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND", "Academic course not found");
    }

    public static ApiException offeringNotFound() {
        return of(HttpStatus.NOT_FOUND, "OFFERING_NOT_FOUND", "Course offering not found");
    }

    public static ApiException roomNotFound() {
        return of(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND", "Room not found");
    }

    // ---------- 409 ----------
    public static ApiException programCodeExists() {
        return of(HttpStatus.CONFLICT, "PROGRAM_CODE_EXISTS", "Academic program code already exists");
    }

    public static ApiException termExists() {
        return of(HttpStatus.CONFLICT, "TERM_EXISTS",
                "Academic term already exists for this year and term type");
    }

    public static ApiException courseCodeExists() {
        return of(HttpStatus.CONFLICT, "COURSE_CODE_EXISTS", "Course code already exists");
    }

    /** Code name comes from openapi.yaml (POST /offerings). */
    public static ApiException offeringExists() {
        return of(HttpStatus.CONFLICT, "OFFERING_EXISTS",
                "Course offering already exists for this course, term and section");
    }

    public static ApiException roomCodeExists() {
        return of(HttpStatus.CONFLICT, "ROOM_CODE_EXISTS", "Room code already exists");
    }

    // ---------- 400 ----------
    /** Validation error that says which field is wrong (details.field / details.problem). */
    public static ApiException validation(String field, String message) {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                message,
                ApiException.fieldDetails(field, message)
        );
    }

    private static ApiException of(HttpStatus status, String code, String message) {
        return new ApiException(status, code, message);
    }
}
