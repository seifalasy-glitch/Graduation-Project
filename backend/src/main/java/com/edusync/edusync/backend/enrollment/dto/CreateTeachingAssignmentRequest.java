package com.edusync.edusync.backend.enrollment.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateTeachingAssignmentRequest(

        @NotNull(message = "Doctor user ID is required")
        Long doctorUserId,

        LocalDate startsAt
) {
}
