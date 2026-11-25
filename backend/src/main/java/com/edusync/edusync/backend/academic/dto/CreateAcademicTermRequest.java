package com.edusync.edusync.backend.academic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CreateAcademicTermRequest(

        @NotBlank(message = "Academic year is required")
        String academicYear,

        @NotBlank(message = "Term type is required")
        String termType,

        @NotNull(message = "Start date is required")
        LocalDate startDate,

        @NotNull(message = "End date is required")
        LocalDate endDate

) {
}
