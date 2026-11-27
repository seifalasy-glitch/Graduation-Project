package com.edusync.edusync.backend.admin.dto;

/** API contract: TemporaryPassword (only the new temporary password; the admin already knows the user id). */
public record ResetPasswordResponse(
        String temporaryPassword
) {
}
