package com.edusync.edusync.backend.auth.dto;

import java.util.List;

public record LoginResponse(
        String accessToken,
        String tokenType,
        Long userId,
        String fullName,
        String email,
        List<String> roles,
        boolean mustChangePassword
) {
}