package com.edusync.edusync.backend.enrollment.dto;

public record GroupResponse(
        Long groupId,
        Long offeringId,
        String groupType,
        String label,
        String dayOfWeek,
        String startTime,
        String endTime,
        Integer memberCount
) {
}
