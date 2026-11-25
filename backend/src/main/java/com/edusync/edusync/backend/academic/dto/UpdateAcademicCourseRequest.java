package com.edusync.edusync.backend.academic.dto;

import jakarta.validation.constraints.Min;

public record UpdateAcademicCourseRequest(

        String courseCode,

        String title,

        @Min(value = 1, message = "Credit hours must be at least 1")
        Integer creditHours

) {
}
