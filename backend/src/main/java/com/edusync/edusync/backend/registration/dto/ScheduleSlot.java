package com.edusync.edusync.backend.registration.dto;

public record ScheduleSlot(
        String groupType,
        String label,
        String dayOfWeek,
        String startTime,
        String endTime
) {
}
