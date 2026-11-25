package com.edusync.edusync.backend.academic.dto;

public record UpdateAcademicProgramRequest(
        String code,
        String name,
        String department,
        Boolean active
) {
}