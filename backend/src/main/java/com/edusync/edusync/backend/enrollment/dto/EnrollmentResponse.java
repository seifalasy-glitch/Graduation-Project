package com.edusync.edusync.backend.enrollment.dto;

public record EnrollmentResponse(
        Long enrollmentId,
        Long offeringId,
        StudentRef student,
        Long programId,
        String programName,
        String status,
        String seatNo
) {
}
