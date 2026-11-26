package com.edusync.edusync.backend.registration.dto;

import com.edusync.edusync.backend.enrollment.dto.DoctorRef;
import com.edusync.edusync.backend.enrollment.dto.StudentRef;

import java.time.Instant;
import java.util.List;

public record RegistrationResponse(
        Long requestId,
        StudentRef student,
        Long termId,
        String status,
        Integer totalCredits,
        Integer maxCreditHours,
        DoctorRef advisor,
        String note,
        Instant submittedAt,
        Instant advisorDecidedAt,
        Instant adminDecidedAt,
        List<RegistrationItemResponse> items
) {
}
