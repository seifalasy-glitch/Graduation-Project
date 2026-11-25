package com.edusync.edusync.backend.academic.dto;

import java.time.LocalDate;

public record AcademicTermResponse(
        Long termId,

        String academicYear,

        String termType,

        LocalDate startDate,

        LocalDate endDate
) {
}
