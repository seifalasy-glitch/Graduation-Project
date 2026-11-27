package com.edusync.edusync.backend.academic.dto;

/**
 * API contract: Program.
 * studentCount = ACTIVE students whose profile is in this program.
 * courseCount  = different courses in this program's study plan.
 */
public record AcademicProgramResponse(
        Long programId,
        String code,
        String name,
        String department,
        Boolean active,
        long studentCount,
        long courseCount
) {
}
