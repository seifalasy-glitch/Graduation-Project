package com.edusync.edusync.backend.admin.dto;

public record ResetPasswordResponse(
        Long userId,
        String temporaryPassword
) {
}
