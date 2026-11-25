package com.edusync.edusync.backend.enrollment.dto;

public record EnrollmentImportRow(
        int number,
        String id,
        String program,
        String seat,
        String lecture,
        String lab
) {
}
