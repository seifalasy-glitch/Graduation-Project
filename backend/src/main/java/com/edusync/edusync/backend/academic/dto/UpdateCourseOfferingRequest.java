package com.edusync.edusync.backend.academic.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;

/**
 * API contract (OfferingUpdate): only section, capacity and courseworkMax can change.
 * The course and the term of an offering never change (students may already be enrolled).
 */
public record UpdateCourseOfferingRequest(

        String section,

        @Min(
                value = 1,
                message = "Capacity must be at least 1"
        )
        Integer capacity,

        @DecimalMin(
                value = "0.0",
                message = "Coursework max cannot be negative"
        )
        BigDecimal courseworkMax

) {
}
