package com.edusync.edusync.backend.enrollment.dto;

public record GroupRequest(
        String groupType,
        String label,
        String dayOfWeek,
        String startTime,
        String endTime
) {
}
