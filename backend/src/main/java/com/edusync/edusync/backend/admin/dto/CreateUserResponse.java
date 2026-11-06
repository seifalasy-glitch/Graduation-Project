package com.edusync.edusync.backend.admin.dto;

public record CreateUserResponse(
        UserResponse user,
        String temporaryPassword
) {
}
