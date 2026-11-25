package com.edusync.edusync.backend.enrollment.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateMembershipRequest(

        @NotNull(message = "Enrollment ID is required")
        Long enrollmentId,

        LocalDate startsAt
) {
}
