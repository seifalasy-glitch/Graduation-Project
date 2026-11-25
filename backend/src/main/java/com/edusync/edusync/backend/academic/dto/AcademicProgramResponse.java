package com.edusync.edusync.backend.academic.dto;

public record AcademicProgramResponse(
        Long programId,
        String code,
        String name,
        String department,
        Boolean active
) {
}