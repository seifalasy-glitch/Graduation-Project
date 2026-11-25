package com.edusync.edusync.backend.academic.dto;

public record AcademicCourseResponse(
        Long courseId,

        String courseCode,

        String title,

        Integer creditHours
) {
}
