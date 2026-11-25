package com.edusync.edusync.backend.enrollment.dto;

import java.time.LocalDate;

public record TeachingAssignmentResponse(
        Long assignmentId,
        Long groupId,
        DoctorRef doctor,
        LocalDate startsAt,
        LocalDate endsAt
) {
}
