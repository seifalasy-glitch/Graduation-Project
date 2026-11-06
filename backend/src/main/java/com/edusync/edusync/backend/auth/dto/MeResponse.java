package com.edusync.edusync.backend.auth.dto;

import java.util.List;

public record MeResponse(
        Long userId,
        String universityId,
        String email,
        String fullName,
        String fullNameAr,
        List<String> roles,
        String status,
        Boolean mustChangePassword
) {
}
