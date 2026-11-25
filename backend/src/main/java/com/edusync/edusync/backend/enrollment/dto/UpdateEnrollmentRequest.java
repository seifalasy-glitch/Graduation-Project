package com.edusync.edusync.backend.enrollment.dto;

public record UpdateEnrollmentRequest(
        Long programId,
        String status,
        String seatNo
) {
}
