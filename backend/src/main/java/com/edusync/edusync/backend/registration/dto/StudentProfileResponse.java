package com.edusync.edusync.backend.registration.dto;

import com.edusync.edusync.backend.enrollment.dto.DoctorRef;

public record StudentProfileResponse(
        Long userId,
        Long programId,
        String programName,
        Integer level,
        Integer maxCreditHours,
        DoctorRef advisor
) {
}
