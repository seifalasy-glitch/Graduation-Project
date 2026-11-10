package com.edusync.edusync.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(

        @NotBlank
        String currentPassword,

        // API contract: at least 8 characters with an uppercase letter,
        // a lowercase letter, a digit and a symbol. Otherwise WEAK_PASSWORD.
        @NotBlank
        @Size(max = 72)
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$")
        String newPassword

) {
}
