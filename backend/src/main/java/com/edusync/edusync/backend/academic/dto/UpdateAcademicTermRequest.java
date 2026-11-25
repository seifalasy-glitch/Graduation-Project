package com.edusync.edusync.backend.academic.dto;

import java.time.LocalDate;

public record UpdateAcademicTermRequest(

        String academicYear,

        String termType,

        LocalDate startDate,

        LocalDate endDate

) {
}
