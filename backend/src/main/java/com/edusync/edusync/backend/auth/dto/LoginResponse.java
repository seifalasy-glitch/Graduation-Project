package com.edusync.edusync.backend.auth.dto;

/**
 * Same shape as LoginResponse in the API contract.
 * Also returned by POST /auth/change-password, with a fresh token.
 */
public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        boolean mustChangePassword,
        MeResponse user
) {
}
