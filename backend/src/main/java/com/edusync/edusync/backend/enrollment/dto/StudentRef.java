package com.edusync.edusync.backend.enrollment.dto;

public record StudentRef(
        Long userId,
        String universityId,
        String fullName,
        String fullNameAr
) {
}
