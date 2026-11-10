package com.edusync.edusync.backend.auth.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The "User" object of the API contract. Never contains the password hash.
 */
public record MeResponse(
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
