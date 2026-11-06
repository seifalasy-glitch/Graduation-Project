package com.edusync.edusync.backend.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateUserRequest(

        @NotBlank
        String universityId,

        @NotBlank
        @Email
        String email,

        @NotBlank
        String fullName,

        String fullNameAr,

        @NotNull
        @NotEmpty
        List<@NotBlank String> roles
) {
}
