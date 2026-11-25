package com.edusync.edusync.backend.enrollment.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record EndPeriodRequest(

        @NotNull(message = "End date is required")
        LocalDate endsAt
) {
}
