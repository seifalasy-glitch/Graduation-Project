package com.edusync.edusync.backend.registration.dto;

import com.edusync.edusync.backend.academic.dto.AcademicCourseResponse;

public record PlanCourseResponse(
        Long planCourseId,
        Long programId,
        AcademicCourseResponse course,
        Integer level,
        String termType
) {
}
