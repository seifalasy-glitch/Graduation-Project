package com.edusync.edusync.backend.academic.dto;

import java.math.BigDecimal;

/**
 * API contract (Offering): the course and the term are sent as objects, not as ids.
 * enrolledCount is added when the enrollments module exists.
 */
public record CourseOfferingResponse(

        Long offeringId,

        AcademicCourseResponse course,

        AcademicTermResponse term,

        String section,

        Integer capacity,

        BigDecimal courseworkMax

) {
}
