package com.edusync.edusync.backend.dashboard.dto;

import com.edusync.edusync.backend.enrollment.dto.GroupResponse;

import java.math.BigDecimal;

/** API contract: TeachingGroup (GET /me/teaching-groups, doctor home screen). */
public record TeachingGroupResponse(
        Long teachingAssignmentId,
        GroupResponse group,
        Long offeringId,
        String courseCode,
        String courseTitle,
        String termLabel,
        int enrolledCount,
        Integer capacity,
        BigDecimal avgAttendancePercent   // all roster slots of finished sessions; null = none finished yet
) {
}
