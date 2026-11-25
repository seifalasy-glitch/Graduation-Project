package com.edusync.edusync.backend.enrollment.dto;

import jakarta.validation.constraints.NotNull;

public record CreateEnrollmentRequest(

        @NotNull(message = "Student user ID is required")
        Long studentUserId,

        @NotNull(message = "Program ID is required")
        Long programId,

        String seatNo
) {
}
