package com.edusync.edusync.backend.enrollment.dto;

import com.edusync.edusync.backend.enrollment.entity.Enrollment;
import com.edusync.edusync.backend.enrollment.entity.GroupMembership;

import java.util.List;
import java.util.Set;

public record EnrollmentImportPlan(
        Long studentId,
        Long programId,
        String seat,
        Enrollment old,
        Set<Long> desired,
        List<GroupMembership> current
) {
}
