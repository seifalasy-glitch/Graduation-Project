package com.edusync.edusync.backend.academic.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateAcademicCourseRequest(

        @NotBlank(message = "Course code is required")
        String courseCode,

        @NotBlank(message = "Course title is required")
        String title,

        @NotNull(message = "Credit hours are required")
        @Min(value = 1, message = "Credit hours must be at least 1")
        Integer creditHours

) {
}
