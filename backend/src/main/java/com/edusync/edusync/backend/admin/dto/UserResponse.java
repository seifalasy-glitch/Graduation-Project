package com.edusync.edusync.backend.admin.dto;

import java.time.LocalDateTime;
import java.util.List;

public record UserResponse(
        Long userId,
        String universityId,
        String email,
        String fullName,
        String fullNameAr,
        List<String> roles,
        String status,
        Boolean mustChangePassword,
        LocalDateTime createdAt
) {
}
