package com.edusync.edusync.backend.dashboard.dto;

import com.edusync.edusync.backend.enrollment.dto.GroupResponse;

import java.math.BigDecimal;
import java.util.List;

/** API contract: MyEnrollment (GET /me/enrollments, student home screen). */
public record MyEnrollmentResponse(
        Long enrollmentId,
        Long offeringId,
        String courseCode,
        String courseTitle,
        String termLabel,
        String programName,
        List<String> doctorNames,
        BigDecimal attendancePercent,   // null until the attendance module exists
        List<GroupResponse> groups
) {
}
