package com.edusync.edusync.backend.academic.dto;

import java.math.BigDecimal;

/**
 * API contract (Offering): the course and the term are sent as objects, not as ids.
 * enrolledCount = ACTIVE enrollments (the seats that count against capacity).
 */
public record CourseOfferingResponse(

        Long offeringId,

        AcademicCourseResponse course,

        AcademicTermResponse term,

        String section,

        Integer capacity,

        BigDecimal courseworkMax,

        long enrolledCount

) {
}
