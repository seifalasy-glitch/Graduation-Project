package com.edusync.edusync.backend.registration.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record StudentProfileRequest(
        @NotNull Long programId,
        @NotNull @Min(1) Integer level,
        @NotNull @Min(1) @Max(30) Integer maxCreditHours,
        Long advisorId
) {
}
