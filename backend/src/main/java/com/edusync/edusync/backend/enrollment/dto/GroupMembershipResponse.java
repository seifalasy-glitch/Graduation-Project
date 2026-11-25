package com.edusync.edusync.backend.enrollment.dto;

import java.time.LocalDate;

public record GroupMembershipResponse(
        Long membershipId,
        Long groupId,
        Long enrollmentId,
        StudentRef student,
        LocalDate startsAt,
        LocalDate endsAt
) {
}
