package com.edusync.edusync.backend.academic.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CreateCourseOfferingRequest(

        @NotNull(message = "Course ID is required")
        Long courseId,

        @NotNull(message = "Term ID is required")
        Long termId,

        @NotBlank(message = "Section is required")
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
