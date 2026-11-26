package com.edusync.edusync.backend.registration.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PlanCourseRequest(
        @NotNull Long courseId,
        @NotNull @Min(1) Integer level,
        @NotBlank String termType
) {
}
